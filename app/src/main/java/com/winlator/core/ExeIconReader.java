package com.winlator.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WinPocket addition (not part of upstream Winlator).
 * Reads the program icon out of a Windows .exe, the one Explorer would show: the first icon group
 * in the file's resources. Plain Java with no Android classes, so it can be tested anywhere.
 */
public final class ExeIconReader {
    private static final int RT_ICON = 3;
    private static final int RT_GROUP_ICON = 14;
    private static final int MAX_FILE_SIZE = 64 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    /** One image from an icon group, as stored in the exe: a PNG file or a Windows DIB. */
    public static final class IconImage {
        public final int width;
        public final int height;
        public final int bitCount;
        public final byte[] data;

        IconImage(int width, int height, int bitCount, byte[] data) {
            this.width = width;
            this.height = height;
            this.bitCount = bitCount;
            this.data = data;
        }

        public boolean isPng() {
            if (data.length < PNG_SIGNATURE.length) return false;
            for (int i = 0; i < PNG_SIGNATURE.length; i++) if (data[i] != PNG_SIGNATURE[i]) return false;
            return true;
        }
    }

    /** Decoded pixels in ARGB order, row by row from the top. */
    public static final class Pixels {
        public final int width;
        public final int height;
        public final int[] argb;

        Pixels(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    private final ByteBuffer buffer;
    private final int[][] sections;
    private int resourceBase = -1;

    private ExeIconReader(byte[] bytes) {
        buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        sections = readSections();
    }

    /** The images of the exe's program icon, largest and deepest colour first. Empty if none. */
    public static List<IconImage> read(File exeFile) {
        try {
            byte[] bytes = readFile(exeFile);
            return bytes != null ? new ExeIconReader(bytes).readMainIcon() : Collections.emptyList();
        }
        catch (IOException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    public static List<IconImage> read(byte[] exeBytes) {
        try {
            return new ExeIconReader(exeBytes).readMainIcon();
        }
        catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private static byte[] readFile(File file) throws IOException {
        long length = file.length();
        if (length <= 0 || length > MAX_FILE_SIZE) return null;
        byte[] bytes = new byte[(int)length];
        try (InputStream inStream = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = inStream.read(bytes, offset, bytes.length - offset);
                if (read < 0) return null;
                offset += read;
            }
        }
        return bytes;
    }

    private int u8(int offset) {
        return buffer.get(offset) & 0xff;
    }

    private int u16(int offset) {
        return buffer.getShort(offset) & 0xffff;
    }

    private int i32(int offset) {
        return buffer.getInt(offset);
    }

    private int[][] readSections() {
        if (u16(0) != 0x5a4d) return null; // "MZ"
        int peOffset = i32(0x3c);
        if (i32(peOffset) != 0x00004550) return null; // "PE\0\0"

        int coffHeader = peOffset + 4;
        int sectionCount = u16(coffHeader + 2);
        int optionalHeaderSize = u16(coffHeader + 16);
        int optionalHeader = coffHeader + 20;
        int magic = u16(optionalHeader);
        boolean is64 = magic == 0x20b;
        if (!is64 && magic != 0x10b) return null;

        int directoryCount = i32(optionalHeader + (is64 ? 108 : 92));
        int directories = optionalHeader + (is64 ? 112 : 96);
        int resourceRva = directoryCount > 2 ? i32(directories + 2 * 8) : 0;

        int sectionTable = optionalHeader + optionalHeaderSize;
        int[][] result = new int[sectionCount][];
        for (int i = 0; i < sectionCount; i++) {
            int section = sectionTable + i * 40;
            // virtual address, virtual size, raw size, raw offset
            result[i] = new int[]{i32(section + 12), i32(section + 8), i32(section + 16), i32(section + 20)};
        }

        if (resourceRva != 0) resourceBase = rvaToOffset(result, resourceRva);
        return result;
    }

    private static int rvaToOffset(int[][] sections, int rva) {
        for (int[] section : sections) {
            int start = section[0];
            int size = Math.max(section[1], section[2]);
            if (rva >= start && rva < start + size) return section[3] + (rva - start);
        }
        return -1;
    }

    /** Entries of a resource directory as {id, offsetToData}; named entries get id -1. */
    private List<int[]> directoryEntries(int directory) {
        int namedCount = u16(directory + 12);
        int idCount = u16(directory + 14);
        List<int[]> entries = new ArrayList<>(namedCount + idCount);
        for (int i = 0; i < namedCount + idCount; i++) {
            int entry = directory + 16 + i * 8;
            int name = i32(entry);
            int id = (name & 0x80000000) != 0 ? -1 : (name & 0xffff);
            entries.add(new int[]{id, i32(entry + 4)});
        }
        return entries;
    }

    private int subdirectory(int offsetToData) {
        return (offsetToData & 0x80000000) != 0 ? resourceBase + (offsetToData & 0x7fffffff) : -1;
    }

    private int findSubdirectory(int directory, int id) {
        for (int[] entry : directoryEntries(directory)) {
            if (entry[0] == id) return subdirectory(entry[1]);
        }
        return -1;
    }

    /** Data of the first language of a resource whose directory is given. */
    private byte[] firstLanguageData(int nameDirectory) {
        if (nameDirectory < 0) return null;
        for (int[] entry : directoryEntries(nameDirectory)) {
            if ((entry[1] & 0x80000000) != 0) continue;
            int dataEntry = resourceBase + entry[1];
            int dataOffset = rvaToOffset(sections, i32(dataEntry));
            int size = i32(dataEntry + 4);
            if (dataOffset < 0 || size <= 0 || dataOffset + size > buffer.capacity()) return null;
            byte[] data = new byte[size];
            buffer.position(dataOffset);
            buffer.get(data);
            return data;
        }
        return null;
    }

    private List<IconImage> readMainIcon() {
        if (sections == null || resourceBase < 0) return Collections.emptyList();

        int groupTypeDirectory = findSubdirectory(resourceBase, RT_GROUP_ICON);
        int iconTypeDirectory = findSubdirectory(resourceBase, RT_ICON);
        if (groupTypeDirectory < 0 || iconTypeDirectory < 0) return Collections.emptyList();

        List<int[]> groups = directoryEntries(groupTypeDirectory);
        if (groups.isEmpty()) return Collections.emptyList();
        byte[] group = firstLanguageData(subdirectory(groups.get(0)[1]));
        if (group == null || group.length < 6) return Collections.emptyList();

        ByteBuffer groupBuffer = ByteBuffer.wrap(group).order(ByteOrder.LITTLE_ENDIAN);
        int count = groupBuffer.getShort(4) & 0xffff;
        List<IconImage> images = new ArrayList<>();

        for (int i = 0; i < count && 6 + (i + 1) * 14 <= group.length; i++) {
            int entry = 6 + i * 14;
            int width = group[entry] & 0xff;
            int height = group[entry + 1] & 0xff;
            int bitCount = groupBuffer.getShort(entry + 6) & 0xffff;
            int iconId = groupBuffer.getShort(entry + 12) & 0xffff;

            byte[] data = firstLanguageData(findSubdirectory(iconTypeDirectory, iconId));
            if (data == null) continue;
            images.add(new IconImage(width == 0 ? 256 : width, height == 0 ? 256 : height, bitCount, data));
        }

        Collections.sort(images, (a, b) -> {
            if (a.width != b.width) return b.width - a.width;
            return b.bitCount - a.bitCount;
        });
        return images;
    }

    /**
     * Decodes an icon image stored as a Windows DIB (not PNG). Handles 32, 24, 8, 4 and 1 bits
     * per pixel with the transparency mask. Returns null for anything else.
     */
    public static Pixels decodeDib(byte[] data) {
        try {
            ByteBuffer dib = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int headerSize = dib.getInt(0);
            int width = dib.getInt(4);
            int height = dib.getInt(8) / 2; // icons store the colour image and the mask
            int bitCount = dib.getShort(14) & 0xffff;
            int compression = dib.getInt(16);
            int colorsUsed = headerSize >= 36 ? dib.getInt(32) : 0;
            if (width <= 0 || height <= 0 || width > 1024 || height > 1024) return null;
            if (compression != 0 && compression != 3) return null; // BI_RGB or BI_BITFIELDS
            if (bitCount != 32 && bitCount != 24 && bitCount != 8 && bitCount != 4 && bitCount != 1) return null;

            int paletteSize = bitCount <= 8 ? (colorsUsed > 0 ? colorsUsed : 1 << bitCount) : 0;
            int palette = headerSize + (compression == 3 && headerSize == 40 ? 12 : 0);
            int pixelData = palette + paletteSize * 4;
            int colorStride = ((width * bitCount + 31) / 32) * 4;
            int maskData = pixelData + colorStride * height;
            int maskStride = ((width + 31) / 32) * 4;
            boolean hasMask = maskData + maskStride * height <= data.length;

            int[] argb = new int[width * height];
            boolean anyAlpha = false;

            for (int y = 0; y < height; y++) {
                int row = pixelData + (height - 1 - y) * colorStride;
                for (int x = 0; x < width; x++) {
                    int color;
                    if (bitCount == 32) {
                        int p = row + x * 4;
                        int alpha = data[p + 3] & 0xff;
                        if (alpha != 0) anyAlpha = true;
                        color = (alpha << 24) | ((data[p + 2] & 0xff) << 16) | ((data[p + 1] & 0xff) << 8) | (data[p] & 0xff);
                    }
                    else if (bitCount == 24) {
                        int p = row + x * 3;
                        color = 0xff000000 | ((data[p + 2] & 0xff) << 16) | ((data[p + 1] & 0xff) << 8) | (data[p] & 0xff);
                    }
                    else {
                        int bitOffset = x * bitCount;
                        int value = (data[row + bitOffset / 8] & 0xff) >> (8 - bitCount - (bitOffset % 8)) & ((1 << bitCount) - 1);
                        int p = palette + value * 4;
                        color = 0xff000000 | ((data[p + 2] & 0xff) << 16) | ((data[p + 1] & 0xff) << 8) | (data[p] & 0xff);
                    }
                    argb[y * width + x] = color;
                }
            }

            // Without an alpha channel, the mask says which pixels are see-through.
            if ((bitCount != 32 || !anyAlpha) && hasMask) {
                for (int y = 0; y < height; y++) {
                    int row = maskData + (height - 1 - y) * maskStride;
                    for (int x = 0; x < width; x++) {
                        boolean transparent = ((data[row + x / 8] >> (7 - x % 8)) & 1) != 0;
                        int index = y * width + x;
                        argb[index] = transparent ? 0 : (argb[index] | 0xff000000);
                    }
                }
            }
            else if (bitCount == 32 && !anyAlpha) {
                for (int i = 0; i < argb.length; i++) argb[i] |= 0xff000000;
            }

            return new Pixels(width, height, argb);
        }
        catch (RuntimeException e) {
            return null;
        }
    }
}
