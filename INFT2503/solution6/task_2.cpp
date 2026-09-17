#include <boost/asio.hpp>
#include <iostream>
#include <memory>
#include <string>

using namespace std;
using namespace boost::asio::ip;

class WebServer {
private:
  class Connection {
  public:
    tcp::socket socket;

    explicit Connection(boost::asio::io_context &io_context) : socket(io_context) {}
  };

  boost::asio::io_context io_context;
  tcp::endpoint endpoint;
  tcp::acceptor acceptor;

  string create_http_response(const string &status, const string &body) {
    return "HTTP/1.1 " + status + "\r\n"
           "Content-Type: text/plain; charset=utf-8\r\n"
           "Content-Length: " + to_string(body.size()) + "\r\n"
           "\r\n" +
           body;
  }

  string create_response(const string &request_line) {
    if (request_line == "GET / HTTP/1.1") {
      return create_http_response("200 OK", "Dette er hovedsiden");
    }
    if (request_line == "GET /en_side HTTP/1.1") {
      return create_http_response("200 OK", "Dette er en side");
    }
    return create_http_response("404 Not Found", "404 Not Found");
  }

  void handle_request(shared_ptr<Connection> connection) {
    auto read_buffer = make_shared<boost::asio::streambuf>();

    async_read_until(connection->socket, *read_buffer, "\r\n",

      [this, connection, read_buffer](const boost::system::error_code &ec, size_t) {
        if (ec)
          return;

        istream read_stream(read_buffer.get());
        string request_line;
        getline(read_stream, request_line);

        if (!request_line.empty() && request_line.back() == '\r')
          request_line.pop_back();

        cout << "Request: " << request_line << endl;

        auto response = make_shared<string>(create_response(request_line));

        async_write(connection->socket, boost::asio::buffer(*response),
          [connection, response](const boost::system::error_code &, size_t) {});
    });
  }

  void accept() {
    auto connection = make_shared<Connection>(io_context);

    acceptor.async_accept(connection->socket, [this, connection](const boost::system::error_code &ec) {
      accept();

      if (!ec)
        handle_request(connection);
    });
  }

public:
  WebServer() : endpoint(tcp::v4(), 8080), acceptor(io_context, endpoint) {}

  void start() {
    accept();
    io_context.run();
  }
};

int main() {
  WebServer server;

  cout << "Starting web server on http://localhost:8080" << endl;

  server.start();
}
