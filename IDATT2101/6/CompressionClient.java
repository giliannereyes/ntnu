import java.io.*;
import java.util.*;

/**
 * Entry point and client for file compression and decompression.
 */
public class CompressionClient {
  public static void main(String[] args) throws IOException {
    Scanner scanner = new Scanner(System.in);
    Compressor compressor = new Compressor();
    Decompressor decompressor = new Decompressor();
    System.out.println("=== File Compression and Decompression ===");
    System.out.println("1. Compress a file");
    System.out.println("2. Decompress a file");
    System.out.println("3. Exit");
    System.out.print("Enter your choice: ");
    int choice;
    try {
      choice = Integer.parseInt(scanner.nextLine().trim());
    } catch (NumberFormatException e) {
      choice = 3;
    }
    switch (choice) {
      case 1 -> {
        System.out.print("Enter file path to compress: ");
        String filePath = scanner.nextLine().trim();
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
          System.out.println("Error: File not found.");
          return;
        }
        System.out.printf("\nOriginal file: %s (%d bytes)%n", inputFile.getName(), inputFile.length());
        File compressedFile = compressor.compress(inputFile);
        System.out.printf("Compressed file: %s (%d bytes)%n", compressedFile.getName(), compressedFile.length());
      }
      case 2 -> {
        System.out.print("Enter file path to decompress: ");
        String filePath = scanner.nextLine().trim();
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
          System.out.println("Error: File not found.");
          return;
        }
        System.out.printf("\nCompressed file: %s (%d bytes)%n", inputFile.getName(), inputFile.length());
        File decompressedFile = decompressor.decompress(inputFile);
        System.out.printf("Decompressed file: %s (%d bytes)%n", decompressedFile.getName(), decompressedFile.length());
      }
      default -> {
        System.out.println("Exiting program...");
      }
    }
  }

  /**
   * The class provides functionality for compressing files using
   * the LZ77 algorithm {@link LZ77Hash} combined with Huffman coding {@link Huffman}.
   * It reads the input file, tokenizes the data into matches and literals, computes frequency tables,
   * builds Huffman trees, and writes the compressed data to an output file.
   */
  public static class Compressor {
    private static final int WINDOW = 1 << 16;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 258;

    public File compress(File inputFile) {
      try {
        byte[] data = readAll(inputFile);
        List<Token> tokens = LZ77Hash.tokenize(data, WINDOW, MIN_MATCH, MAX_MATCH);
        int[] litFreq = computeLiteralFrequencies(tokens);
        int[] lenFreq = computeLengthFrequencies(tokens);
        ensureNonZeroFrequencies(litFreq, lenFreq);
        Huffman litHuff = new Huffman(litFreq);
        Huffman lenHuff = new Huffman(lenFreq);
        File outputFile = new File("compressed_" + inputFile.getName());
        writeCompressedFile(outputFile, tokens, litFreq, lenFreq, litHuff, lenHuff);
        return outputFile;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private int[] computeLiteralFrequencies(List<Token> tokens) {
      int[] litFreq = new int[256];
      for (Token t : tokens) {
        if (t instanceof LiteralRun) {
          for (byte b : ((LiteralRun) t).data) litFreq[b & 0xFF]++;
        }
      }
      return litFreq;
    }

    private int[] computeLengthFrequencies(List<Token> tokens) {
      int[] lenFreq = new int[MAX_MATCH + 1];
      for (Token t : tokens) {
        if (t instanceof Match) {
          lenFreq[((Match) t).length]++;
        }
      }
      return lenFreq;
    }

    private void ensureNonZeroFrequencies(int[] litFreq, int[] lenFreq) {
      litFreq[0] = Math.max(litFreq[0], 1);
      lenFreq[MIN_MATCH] = Math.max(lenFreq[MIN_MATCH], 1);
    }

    private void writeCompressedFile(File outputFile, List<Token> tokens, int[] litFreq, int[] lenFreq,
                                     Huffman litHuff, Huffman lenHuff) throws IOException {
      try (BitOutput out = new BitOutput(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
        writeHeader(out, litFreq, lenFreq);
        writeTokens(out, tokens, litHuff, lenHuff);
        writeFooter(out);
      }
    }

    private void writeHeader(BitOutput out, int[] litFreq, int[] lenFreq) throws IOException {
      out.writeBytes("LZH2".getBytes());
      out.writeInt(WINDOW);
      out.writeInt(MAX_MATCH);
      for (int i = 0; i < 256; i++) out.writeInt(litFreq[i]);
      for (int i = 0; i <= MAX_MATCH; i++) out.writeInt(lenFreq[i]);
    }

    private void writeTokens(BitOutput out, List<Token> tokens, Huffman litHuff, Huffman lenHuff) throws IOException {
      for (Token t : tokens) {
        if (t instanceof Match m) {
          writeMatch(out, m, lenHuff);
        } else {
          writeLiteralRun(out, (LiteralRun) t, litHuff);
        }
      }
    }

    private void writeMatch(BitOutput out, Match m, Huffman lenHuff) throws IOException {
      out.writeBit(1);
      out.writeShort(m.distance);
      Huffman.Code c = lenHuff.codes[m.length];
      out.writeBits(c.bits, c.nBits);
    }

    private void writeLiteralRun(BitOutput out, LiteralRun lr, Huffman litHuff) throws IOException {
      out.writeBit(0);
      out.writeShort(lr.data.length);
      for (byte b : lr.data) {
        Huffman.Code c = litHuff.codes[b & 0xFF];
        out.writeBits(c.bits, c.nBits);
      }
    }

    private void writeFooter(BitOutput out) throws IOException {
      out.writeBit(0);
      out.writeShort(0);
      out.flushBits();
    }
  }

  /**
   * The class provides functionality for decompressing files that were compressed using the
   * LZ77 algorithm {@link LZ77Hash} combined with Huffman coding {@link Huffman}.
   * It reads the compressed file, decodes the header, reconstructs the Huffman
   * trees, and decompresses the data into its original form.
   */
  public static class Decompressor {
    public File decompress(File inputFile) {
      try (BitInput in = new BitInput(new BufferedInputStream(new FileInputStream(inputFile)))) {
        int a = in.readByte(), b = in.readByte(), c = in.readByte(), d = in.readByte();
        if (a != 'L' || b != 'Z' || c != 'H' || (d != '2')) {
          throw new IOException("Bad header");
        }
        int windowSize = in.readInt();
        int maxMatch = in.readInt();
        int[] litFreq = readLiteralFrequencies(in);
        int[] lenFreq = readLengthFrequencies(in, maxMatch);
        Huffman litHuff = new Huffman(litFreq);
        Huffman lenHuff = new Huffman(lenFreq);
        byte[] out = new byte[Math.max(8192, windowSize * 2)];
        int outLen = 0;
        while (true) {
          int tag = in.readBit();
          if (tag < 0) break;

          if (tag == 1) {
            int dist = in.readShort() & 0xFFFF;
            int len = lenHuff.decode(in);
            out = ensureCapacity(out, outLen + len);
            for (int k = 0; k < len; k++) {
              out[outLen] = out[outLen - dist];
              outLen++;
            }
          } else {
            int run = in.readShort() & 0xFFFF;
            if (run == 0) break;
            out = ensureCapacity(out, outLen + run);
            for (int i = 0; i < run; i++) {
              int sym = litHuff.decode(in);
              out[outLen++] = (byte) sym;
            }
          }
        }
        return writeOutputFile(inputFile, out, outLen);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private int[] readLiteralFrequencies(BitInput in) throws IOException {
      int[] litFreq = new int[256];
      for (int i = 0; i < 256; i++) litFreq[i] = in.readInt();
      return litFreq;
    }

    private int[] readLengthFrequencies(BitInput in, int maxMatch) throws IOException {
      int[] lenFreq = new int[maxMatch + 1];
      for (int i = 0; i <= maxMatch; i++) lenFreq[i] = in.readInt();
      return lenFreq;
    }

    private File writeOutputFile(File inputFile, byte[] out, int outLen) throws IOException {
      String name = inputFile.getName().replaceFirst("compressed_", "decompressed_");
      try (FileOutputStream fOut = new FileOutputStream(name)) {
        fOut.write(out, 0, outLen);
      }
      return new File(name);
    }

    private static byte[] ensureCapacity(byte[] arr, int needed) {
      if (needed <= arr.length) return arr;
      int newCap = arr.length;
      while (newCap < needed) newCap *= 2;
      byte[] n = new byte[newCap];
      System.arraycopy(arr, 0, n, 0, arr.length);
      return n;
    }
  }

  /**
   * The class provides functionality for tokenizing data using the LZ77 compression algorithm.
   * It identifies matches and literals in the input data, which can then be used for further compression.
   */
  static final class LZ77Hash {
    static List<Token> tokenize(byte[] data, int window, int MIN_MATCH, int MAX_MATCH) {
      final int n = data.length;
      ArrayList<Token> out = new ArrayList<>();
      ByteArrayOutputStream litBuf = new ByteArrayOutputStream();
      final int HASH_BITS = 15;
      final int HASH_SIZE = 1 << HASH_BITS;
      final int HASH_MASK = HASH_SIZE - 1;
      final int MAX_CHAIN = 64;
      int[] head = new int[HASH_SIZE];
      int[] prev = new int[n];
      Arrays.fill(head, -1);
      Arrays.fill(prev, -1);
      int i = 0;
      while (i < n) {
        int maxLen = Math.min(MAX_MATCH, n - i);
        int[] best = findBestMatch(data, i, n, head, prev, window, MAX_CHAIN, HASH_MASK, maxLen);
        int bestLen = best[0];
        int bestDist = best[1];
        if (shouldDelayMatch(data, i, n, bestLen, MIN_MATCH, MAX_MATCH, head, prev, window, MAX_CHAIN, HASH_MASK)) {
          emitLiteral(data, i, head, prev, n, HASH_MASK, litBuf);
          i++;
          continue;
        }
        if (bestLen >= MIN_MATCH) {
          flushLiterals(out, litBuf);
          emitMatch(out, data, i, n, bestLen, bestDist, head, prev, HASH_MASK);
          i += bestLen;
        } else {
          emitLiteral(data, i, head, prev, n, HASH_MASK, litBuf);
          flushIfLargeLiteral(out, litBuf);
          i++;
        }
      }
      flushLiterals(out, litBuf);
      return out;
    }

    private static int[] findBestMatch(byte[] data, int i, int n, int[] head, int[] prev,
                                       int window, int MAX_CHAIN, int HASH_MASK, int maxLen) {
      int bestLen = 0, bestDist = 0;
      if (i + 2 < n) {
        int h = hash3(data, i) & HASH_MASK;
        int j = head[h];
        int chain = 0;
        while (j >= 0 && chain++ < MAX_CHAIN && (i - j) <= window) {
          int dist = i - j;
          int len = 0;
          if (data[j] == data[i] && data[j + 1] == data[i + 1] && data[j + 2] == data[i + 2]) {
            while (len < maxLen && data[j + len] == data[i + len]) len++;
            if (len > bestLen) {
              bestLen = len;
              bestDist = dist;
              if (len == maxLen) break;
            }
          }
          j = prev[j];
        }
      }
      return new int[]{bestLen, bestDist};
    }

    private static boolean shouldDelayMatch(byte[] data, int i, int n, int bestLen, int MIN_MATCH, int MAX_MATCH,
                                            int[] head, int[] prev, int window, int MAX_CHAIN, int HASH_MASK) {
      if (bestLen < MIN_MATCH || i + 1 >= n) return false;
      int nextBest = 0;
      int maxLen2 = Math.min(MAX_MATCH, n - (i + 1));
      if (i + 3 < n) {
        int h2 = hash3(data, i + 1) & HASH_MASK;
        int j2 = head[h2];
        int chain = 0;
        while (j2 >= 0 && chain++ < MAX_CHAIN && ((i + 1) - j2) <= window) {
          int len = 0;
          if (data[j2] == data[i + 1] && data[j2 + 1] == data[i + 2] && data[j2 + 2] == data[i + 3]) {
            while (len < maxLen2 && data[j2 + len] == data[i + 1 + len]) len++;
            if (len > nextBest) {
              nextBest = len;
              if (len == maxLen2) break;
            }
          }
          j2 = prev[j2];
        }
      }
      return nextBest > bestLen + 1;
    }

    private static void emitMatch(List<Token> out, byte[] data, int i, int n,
                                  int bestLen, int bestDist, int[] head, int[] prev, int HASH_MASK) {
      out.add(new Match(bestDist, bestLen));
      int end = i + bestLen;
      for (int p = i; p < Math.min(end, n - 2); p++) {
        int h = hash3(data, p) & HASH_MASK;
        prev[p] = head[h];
        head[h] = p;
      }
    }

    private static void emitLiteral(byte[] data, int i, int[] head, int[] prev,
                                    int n, int HASH_MASK, ByteArrayOutputStream litBuf) {
      litBuf.write(data[i] & 0xFF);
      if (i + 2 < n) {
        int h = hash3(data, i) & HASH_MASK;
        prev[i] = head[h];
        head[h] = i;
      }
    }

    private static void flushIfLargeLiteral(List<Token> out, ByteArrayOutputStream litBuf) {
      if (litBuf.size() >= 0xFFFF) {
        out.add(new LiteralRun(litBuf.toByteArray()));
        litBuf.reset();
      }
    }

    private static void flushLiterals(List<Token> out, ByteArrayOutputStream litBuf) {
      if (litBuf.size() > 0) {
        out.add(new LiteralRun(litBuf.toByteArray()));
        litBuf.reset();
      }
    }

    private static int hash3(byte[] d, int i) {
      int v = ((d[i] & 0xFF) << 16) | ((d[i + 1] & 0xFF) << 8) | (d[i + 2] & 0xFF);
      v ^= v >>> 13;
      return (v * 0x9E3779B1);
    }
  }

  static abstract class Token {}

  /**
   * The class represents a token in the LZ77 compression algorithm
   * that encodes a repeated sequence of bytes. It specifies the distance
   * to the start of the repeated sequence and the length of the match.
   */
  static final class Match extends Token {
    final int distance, length;

    Match(int d, int l) {
      distance = d;
      length = l;
    }
  }

  /**
   * The class represents a token in the LZ77 compression algorithm
   * that encodes a sequence of literal bytes. It is used when no match is found
   * for the current data, and the bytes are stored as-is.
   */
  static final class LiteralRun extends Token {
    final byte[] data;

    LiteralRun(byte[] d) {
      data = d;
    }
  }


  /**
   * The class provides functionality for building and using Huffman coding trees.
   * It is used to compress and decompress data by encoding symbols based on their frequencies.
   */
  static final class Huffman {
    final Node root;
    final Code[] codes;

    Huffman(int[] freq) {
      int K = freq.length;
      PriorityQueue<Node> pq = buildInitialQueue(freq, K);
      ensureMinimumSymbols(pq, K);
      Node rootNode = buildTree(pq);
      validateRoot(rootNode);
      root = rootNode;
      codes = new Code[K];
      buildCodes(root, 0L, 0);
    }

    private PriorityQueue<Node> buildInitialQueue(int[] freq, int K) {
      PriorityQueue<Node> pq = new PriorityQueue<>();
      for (int s = 0; s < K; s++) {
        if (freq[s] > 0) pq.add(new Node(s, freq[s]));
      }
      return pq;
    }

    private void ensureMinimumSymbols(PriorityQueue<Node> pq, int K) {
      if (pq.isEmpty()) {
        pq.add(new Node(0, 1));
        pq.add(new Node((1 < K) ? 1 : 0, 1));
      } else if (pq.size() == 1) {
        Node only = pq.poll();
        int other = (only.sym + 1) % K;
        pq.add(only);
        pq.add(new Node(other, 1));
      }
    }

    private Node buildTree(PriorityQueue<Node> pq) {
      while (pq.size() > 1) {
        Node a = pq.poll();
        Node b = pq.poll();
        if (b == null) throw new IllegalStateException("Huffman tree building failed");
        pq.add(new Node(a, b));
      }
      return pq.poll();
    }

    private void validateRoot(Node rootNode) {
      if (rootNode == null) {
        throw new IllegalStateException("Huffman tree building failed. Root is null.");
      }
    }

    private void buildCodes(Node n, long bits, int len) {
      if (n.leaf()) {
        codes[n.sym] = new Code(bits, Math.max(1, len));
        return;
      }
      buildCodes(n.left, bits << 1, len + 1);
      buildCodes(n.right, (bits << 1) | 1L, len + 1);
    }

    int decode(BitInput in) throws IOException {
      Node cur = root;
      while (!cur.leaf()) {
        int bit = in.readBit();
        if (bit < 0) throw new EOFException("EOF in Huffman stream");
        cur = (bit == 0) ? cur.left : cur.right;
      }
      return cur.sym;
    }

    /**
     * The class represents a node in the Huffman tree.
     * It can be either a leaf node (representing a symbol)
     * or an internal node (representing a combination of frequencies).
     */
    static final class Node implements Comparable<Node> {
      final int sym;
      final int freq;
      final Node left, right;

      Node(int s, int f) {
        sym = s;
        freq = f;
        left = null;
        right = null;
      }

      Node(Node a, Node b) {
        sym = -1;
        freq = a.freq + b.freq;
        left = a;
        right = b;
      }

      boolean leaf() {
        return left == null && right == null;
      }

      public int compareTo(Node o) {
        return Integer.compare(this.freq, o.freq);
      }
    }

    /**
     * The class represents a Huffman code for a symbol.
     * It stores the bit sequence and the number of bits used for the code.
     */
    static final class Code {
      final long bits;
      final int nBits;

      Code(long b, int n) {
        bits = b;
        nBits = n;
      }
    }
  }

  /**
   * The class provides functionality for writing data at the bit level to an output stream.
   * It is used to write compressed data in a compact binary format.
   */
  static final class BitOutput implements Closeable {
    private final OutputStream out;
    private int buf = 0, cnt = 0;

    BitOutput(OutputStream o) {
      out = o;
    }

    void writeBit(int b) throws IOException {
      buf = (buf << 1) | (b & 1);
      if (++cnt == 8) flushByte();
    }

    void writeBits(long bits, int n) throws IOException {
      for (int i = n - 1; i >= 0; i--) writeBit((int) ((bits >> i) & 1L));
    }

    void writeShort(int v) throws IOException {
      flushBits();
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    void writeInt(int v) throws IOException {
      flushBits();
      out.write((v >>> 24) & 0xFF);
      out.write((v >>> 16) & 0xFF);
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    void writeBytes(byte[] arr) throws IOException {
      flushBits();
      out.write(arr);
    }

    private void flushByte() throws IOException {
      out.write(buf & 0xFF);
      buf = 0;
      cnt = 0;
    }

    void flushBits() throws IOException {
      if (cnt > 0) {
        buf <<= (8 - cnt);
        flushByte();
      }
    }

    public void close() throws IOException {
      flushBits();
      out.close();
    }
  }

  /**
   * The class provides functionality for reading data at the bit level from an input stream.
   * It is used to read compressed data in a compact binary format.
   */
  static final class BitInput implements Closeable {
    private final InputStream in;
    private int buf = 0;
    private int cnt = 0;

    BitInput(InputStream i) {
      in = i;
    }

    int readBit() throws IOException {
      if (cnt == 0) {
        buf = in.read();
        if (buf < 0) return -1;
        cnt = 8;
      }
      int bit = (buf >> 7) & 1;
      buf <<= 1;
      cnt--;
      return bit;
    }

    int readByte() throws IOException {
      align();
      return in.read();
    }

    int readShort() throws IOException {
      align();
      int a = in.read();
      int b = in.read();
      if (b < 0) throw new EOFException();
      return ((a & 0xFF) << 8) | (b & 0xFF);
    }

    int readInt() throws IOException {
      align();
      int b1 = in.read(), b2 = in.read(), b3 = in.read(), b4 = in.read();
      if (b4 < 0) throw new EOFException();
      return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    void align() {
      buf = 0;
      cnt = 0;
    }

    public void close() throws IOException {
      in.close();
    }
  }

  /**
   * Reads the entire contents of a file into a byte array.
   */
  static byte[] readAll(File f) throws IOException {
    try (InputStream i = new BufferedInputStream(new FileInputStream(f))) {
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] b = new byte[1 << 16];
      int n;
      while ((n = i.read(b)) > 0) o.write(b, 0, n);
      return o.toByteArray();
    }
  }
}
