package com.giliannereyes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Shared binary protocol for UDP dot-product request/response messages.
 *
 * <p>Request format:
 *
 * <ul>
 *   <li>int vectorLength
 *   <li>vectorLength doubles for vector A
 *   <li>vectorLength doubles for vector B
 * </ul>
 *
 * <p>Response format:
 *
 * <ul>
 *   <li>byte status (0 = OK, 1 = ERROR)
 *   <li>if status = OK: double dotProduct
 *   <li>if status = ERROR: int messageLength + UTF-8 message bytes
 * </ul>
 */
public final class UdpDotProductProtocol {
  /** Maximum payload size for one UDP datagram. */
  public static final int MAX_UDP_PAYLOAD_BYTES = 65_507;

  private static final byte STATUS_OK = 0;
  private static final byte STATUS_ERROR = 1;

  private UdpDotProductProtocol() {}

  /**
   * Returns the maximum supported vector length in this protocol for one UDP request packet.
   *
   * @return max vector length
   */
  public static int maxVectorLength() {
    return (MAX_UDP_PAYLOAD_BYTES - Integer.BYTES) / (Double.BYTES * 2);
  }

  /**
   * Encodes two vectors into one UDP request payload.
   *
   * @param first first vector
   * @param second second vector
   * @return encoded request bytes
   * @throws IllegalArgumentException if vectors are null, have different lengths, or are too large
   */
  public static byte[] encodeRequest(double[] first, double[] second) {
    if (first == null || second == null) {
      throw new IllegalArgumentException("Vectors cannot be null.");
    }
    if (first.length != second.length) {
      throw new IllegalArgumentException("Vectors must have the same length.");
    }

    int length = first.length;
    if (length > maxVectorLength()) {
      throw new IllegalArgumentException("Vector length exceeds one UDP packet capacity.");
    }

    int payloadSize = Integer.BYTES + length * Double.BYTES * 2;
    ByteBuffer buffer = ByteBuffer.allocate(payloadSize).order(ByteOrder.BIG_ENDIAN);
    buffer.putInt(length);

    for (double value : first) {
      buffer.putDouble(value);
    }
    for (double value : second) {
      buffer.putDouble(value);
    }

    return buffer.array();
  }

  /**
   * Parses and validates a UDP request payload.
   *
   * @param data datagram data
   * @param dataLength number of valid bytes in {@code data}
   * @return parsed request
   * @throws IllegalArgumentException if payload is malformed
   */
  public static ParsedRequest decodeRequest(byte[] data, int dataLength) {
    if (dataLength < Integer.BYTES) {
      throw new IllegalArgumentException("Payload is too short to contain vector length.");
    }

    ByteBuffer buffer = ByteBuffer.wrap(data, 0, dataLength).order(ByteOrder.BIG_ENDIAN);
    int length = buffer.getInt();
    if (length < 0) {
      throw new IllegalArgumentException("Vector length cannot be negative.");
    }

    long expectedSize = Integer.BYTES + (long) length * Double.BYTES * 2;
    if (expectedSize != dataLength) {
      throw new IllegalArgumentException("Payload size does not match declared vector length.");
    }

    double[] first = new double[length];
    for (int i = 0; i < length; i++) {
      first[i] = buffer.getDouble();
    }

    double[] second = new double[length];
    for (int i = 0; i < length; i++) {
      second[i] = buffer.getDouble();
    }

    return new ParsedRequest(first, second);
  }

  /**
   * Encodes a successful response with the computed dot product.
   *
   * @param dotProduct computed dot product value
   * @return encoded response bytes
   */
  public static byte[] encodeSuccessResponse(double dotProduct) {
    ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES + Double.BYTES).order(ByteOrder.BIG_ENDIAN);
    buffer.put(STATUS_OK);
    buffer.putDouble(dotProduct);
    return buffer.array();
  }

  /**
   * Encodes an error response.
   *
   * @param message error description
   * @return encoded response bytes
   */
  public static byte[] encodeErrorResponse(String message) {
    byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer =
        ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + messageBytes.length).order(ByteOrder.BIG_ENDIAN);
    buffer.put(STATUS_ERROR);
    buffer.putInt(messageBytes.length);
    buffer.put(messageBytes);
    return buffer.array();
  }

  /**
   * Parses a server response payload.
   *
   * @param data datagram data
   * @param dataLength number of valid bytes in {@code data}
   * @return parsed response
   * @throws IllegalArgumentException if payload is malformed
   */
  public static ParsedResponse decodeResponse(byte[] data, int dataLength) {
    if (dataLength < Byte.BYTES) {
      throw new IllegalArgumentException("Response payload is empty.");
    }

    ByteBuffer buffer = ByteBuffer.wrap(data, 0, dataLength).order(ByteOrder.BIG_ENDIAN);
    byte status = buffer.get();

    if (status == STATUS_OK) {
      if (dataLength != Byte.BYTES + Double.BYTES) {
        throw new IllegalArgumentException("Invalid success payload size.");
      }
      return ParsedResponse.success(buffer.getDouble());
    }

    if (status == STATUS_ERROR) {
      if (dataLength < Byte.BYTES + Integer.BYTES) {
        throw new IllegalArgumentException("Invalid error payload size.");
      }
      int messageLength = buffer.getInt();
      if (messageLength < 0 || messageLength != buffer.remaining()) {
        throw new IllegalArgumentException("Invalid error message length.");
      }
      byte[] messageBytes = new byte[messageLength];
      buffer.get(messageBytes);
      return ParsedResponse.error(new String(messageBytes, StandardCharsets.UTF_8));
    }

    throw new IllegalArgumentException("Unknown response status: " + status);
  }

  /** Parsed request vectors from an incoming UDP packet. */
  public record ParsedRequest(double[] first, double[] second) {}

  /** Parsed response from the server. */
  public record ParsedResponse(boolean success, double dotProduct, String errorMessage) {
    /**
     * Creates a successful parsed response.
     *
     * @param dotProduct computed dot product
     * @return response instance
     */
    public static ParsedResponse success(double dotProduct) {
      return new ParsedResponse(true, dotProduct, null);
    }

    /**
     * Creates an error parsed response.
     *
     * @param errorMessage response error message
     * @return response instance
     */
    public static ParsedResponse error(String errorMessage) {
      return new ParsedResponse(false, Double.NaN, errorMessage);
    }
  }
}
