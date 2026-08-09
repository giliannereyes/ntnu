const net = require('net');
const crypto = require('crypto');

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const clients = new Set();

// Simple HTTP server responds with a simple WebSocket client test
const httpServer = net.createServer((connection) => {
  connection.on('data', () => {
    const content = `<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
  </head>
  <body>
    WebSocket test page
    <script>
      let ws = new WebSocket('ws://localhost:3001');
      ws.onmessage = event => alert('Message from server: ' + event.data);
      ws.onopen = () => ws.send('hello');
    </script>
  </body>
</html>`;

    connection.write(
      'HTTP/1.1 200 OK\r\n' +
      `Content-Length: ${content.length}\r\n\r\n` +
      content
    );
  });
});

httpServer.listen(3000, () => {
  console.log('HTTP server listening on port 3000');
});

// WebSocket server
const wsServer = net.createServer((connection) => {
  console.log('Client connected');

  connection.once('data', (data) => {
    const requestText = data.toString('utf8');

    if (!performHandshake(connection, requestText)) return;

    clients.add(connection);  // store client after handshake
    console.log('WebSocket handshake completed');

    connection.on('data', (frame) => {
        const message = decodeFrame(frame);
        console.log('Message received:', message);

        if (message === null) {
          console.log("Ignoring non-text frames")
          return;
        }

        console.log('Broadcasting to', clients.size, 'client(s)');

        const responseFrame = encodeFrame(message);
        for (const client of clients) {
          client.write(responseFrame);
        }
    });
  });

  connection.on('end', () => {
    console.log('Client disconnected');
    clients.delete(connection); // remove disconnected client
  });
});

wsServer.on('error', (error) => {
  console.error('Error:', error);
});

wsServer.listen(3001, () => {
  console.log('WebSocket server listening on port 3001');
});

function parseHeaders(requestText) {
  const headers = {};

  for (const line of requestText.split('\r\n').slice(1)) {
    if (!line) break;

    const [name, ...rest] = line.split(':');
    if (!name || rest.length === 0) continue;

    headers[name.trim().toLowerCase()] = rest.join(':').trim();
  }

  return headers;
}

function createAcceptValue(clientKey) {
  return crypto
    .createHash('sha1')
    .update(clientKey + GUID)
    .digest('base64');
}

function performHandshake(connection, requestText) {
  const headers = parseHeaders(requestText);

  const upgrade = (headers['upgrade'] || '').toLowerCase();
  const connectionHeader = (headers['connection'] || '').toLowerCase();
  const key = headers['sec-websocket-key'];
  const version = headers['sec-websocket-version'];

  const validHandshake =
    upgrade === 'websocket' &&
    connectionHeader.includes('upgrade') &&
    key &&
    version === '13';

  if (!validHandshake) {
    connection.write('HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n');
    connection.end();
    return false;
  }

  const acceptValue = createAcceptValue(key);

  connection.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
    'Upgrade: websocket\r\n' +
    'Connection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${acceptValue}\r\n\r\n`
  );

  return true;
}

function decodeFrame(frame) {
  const firstByte = frame[0];
  const secondByte = frame[1];

  const opcode = firstByte & 0x0f;
  const isMasked = (secondByte & 0x80) !== 0;
  const payloadLength = secondByte & 0x7f;

  if (opcode !== 0x1) {
    return null; // ignore non-text frames
  }

  if (!isMasked) {
    throw new Error('Client frames must be masked');
  }

  if (payloadLength >= 126) {
    throw new Error('Only short messages are supported');
  }

  const maskingKey = frame.slice(2, 6);
  const maskedPayload = frame.slice(6, 6 + payloadLength);
  const payload = Buffer.alloc(payloadLength);

  for (let i = 0; i < payloadLength; i++) {
    payload[i] = maskedPayload[i] ^ maskingKey[i % 4];
  }

  return payload.toString('utf8');
}

function encodeFrame(message) {
  const payload = Buffer.from(message, 'utf8');
  const frame = Buffer.alloc(2 + payload.length);

  frame[0] = 0x81; // FIN = 1, opcode = 1 (text)
  frame[1] = payload.length; // no mask bit set

  payload.copy(frame, 2);

  return frame;
}
