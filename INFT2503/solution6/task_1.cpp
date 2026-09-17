#include <iostream>
#include <functional>
#include <memory>
#include <string>
#include <vector>

using namespace std;

class ChessBoard {
public:
  enum class Color { WHITE,
                     BLACK };

  class Piece {
  public:
    Piece(Color color) : color(color) {}
    virtual ~Piece() {}

    Color color;
    std::string color_string() const {
      if (color == Color::WHITE)
        return "white";
      else
        return "black";
    }

    /// Return color and type of the chess piece
    virtual string type() const = 0;

    /// Returns true if the given chess piece move is valid
    virtual bool valid_move(int from_x, int from_y, int to_x, int to_y) const = 0;

    virtual string symbol() const = 0;
  };

  class King : public Piece {
  public:
    King(Color color) : Piece(color) {}

    string type() const override {
      return color_string() + " king";
    }

    string symbol() const override {
      if (color == Color::WHITE)
        return "K";

      return "k";
    }

    bool valid_move(int from_x, int from_y, int to_x, int to_y) const override {
      int dx = std::abs(to_x - from_x);
      int dy = std::abs(to_y - from_y);
      return dx <= 1 && dy <= 1 && (dx != 0 || dy != 0);
    }
  };

  class Knight : public Piece {
  public:
    Knight(Color color) : Piece(color) {}

    string type() const override {
      return color_string() + " knight";
    }

    string symbol() const override {
      if (color == Color::WHITE)
        return "N";

      return "n";
    }

    bool valid_move(int from_x, int from_y, int to_x, int to_y) const override {
      int dx = std::abs(to_x - from_x);
      int dy = std::abs(to_y - from_y);
      return (dx == 1 && dy == 2) || (dx == 2 && dy == 1);
    }
  };

  ChessBoard() {
    // Initialize the squares stored in 8 columns and 8 rows:
    squares.resize(8);
    for (auto &square_column : squares)
      square_column.resize(8);
  }

  /// 8x8 squares occupied by 1 or 0 chess pieces
  vector<vector<unique_ptr<Piece>>> squares;

  function<void(const Piece &, const string &, const string &)> on_piece_move;
  function<void(const Piece &, const string &)> on_piece_removed;
  function<void(const Piece &)> on_game_lost;
  function<void(const Piece &, const string &, const string &)> on_invalid_move;
  function<void(const string &)> on_no_piece;
  function<void()> after_piece_move;

  /// Move a chess piece if it is a valid move.
  /// Does not test for check or checkmate.
  bool move_piece(const std::string &from, const std::string &to) {
    int from_x = from[0] - 'a';
    int from_y = stoi(string() + from[1]) - 1;
    int to_x = to[0] - 'a';
    int to_y = stoi(string() + to[1]) - 1;

    auto &piece_from = squares[from_x][from_y];
    if (piece_from) {
      if (piece_from->valid_move(from_x, from_y, to_x, to_y)) {
        if (on_piece_move)
          on_piece_move(*piece_from, from, to);

        auto &piece_to = squares[to_x][to_y];
        if (piece_to) {
          if (piece_from->color != piece_to->color) {
            if (on_piece_removed)
              on_piece_removed(*piece_to, to);

            if (auto king = dynamic_cast<King *>(piece_to.get()))
              if (on_game_lost)
                on_game_lost(*king);
          } else {
            // piece in the from square has the same color as the piece in the to square
            if (on_invalid_move)
              on_invalid_move(*piece_from, from, to);

            return false;
          }
        }
        piece_to = std::move(piece_from);

        if (after_piece_move)
          after_piece_move();

        return true;
      }
      if (on_invalid_move)
        on_invalid_move(*piece_from, from, to);

      return false;
    }
    if (on_no_piece)
      on_no_piece(from);

    return false;
  }
};

class ChessBoardPrint {
public:
  ChessBoardPrint(ChessBoard &chess_board) : chess_board_(chess_board) {
    chess_board_.on_piece_move = [](const ChessBoard::Piece &piece, const string &from, const string &to) {
      cout << piece.type() << " is moving from " << from << " to " << to << endl;
    };

    chess_board_.on_piece_removed = [](const ChessBoard::Piece &piece, const string &square) {
      cout << piece.type() << " is being removed from " << square << endl;
    };

    chess_board_.on_game_lost = [](const ChessBoard::Piece &king) {
      cout << king.color_string() << " lost the game" << endl;
    };

    chess_board_.on_invalid_move = [](const ChessBoard::Piece &piece, const string &from, const string &to) {
      cout << "can not move " << piece.type() << " from " << from << " to " << to << endl;
    };

    chess_board_.on_no_piece = [](const string &square) {
      cout << "no piece at " << square << endl;
    };

    chess_board_.after_piece_move = [this]() {
      print_board();
    };
  }

  void print_piece_symbols() const {
    cout << "White king = K" << endl;
    cout << "White knight = N" << endl;
    cout << "Black king = k" << endl;
    cout << "Black knight = n" << endl;
    cout << endl;
  }

  void print_invalid_moves_heading() const {
    cout << "Invalid moves:" << endl;
  }

  void print_simulated_game_heading() const {
    cout << "A simulated game:" << endl;
  }

  void print_empty_line() const {
    cout << endl;
  }

  void print_board() const {
    cout << "  a b c d e f g h" << endl;

    for (int y = 7; y >= 0; --y) {
      cout << y + 1 << " ";

      for (int x = 0; x < 8; ++x) {
        if (chess_board_.squares[x][y])
          cout << chess_board_.squares[x][y]->symbol();
        else
          cout << ".";

        cout << " ";
      }

      cout << endl;
    }
  }

private:
  ChessBoard &chess_board_;
};

int main() {
  ChessBoard board;
  ChessBoardPrint print(board);

  board.squares[4][0] = make_unique<ChessBoard::King>(ChessBoard::Color::WHITE);
  board.squares[1][0] = make_unique<ChessBoard::Knight>(ChessBoard::Color::WHITE);
  board.squares[6][0] = make_unique<ChessBoard::Knight>(ChessBoard::Color::WHITE);

  board.squares[4][7] = make_unique<ChessBoard::King>(ChessBoard::Color::BLACK);
  board.squares[1][7] = make_unique<ChessBoard::Knight>(ChessBoard::Color::BLACK);
  board.squares[6][7] = make_unique<ChessBoard::Knight>(ChessBoard::Color::BLACK);

  print.print_piece_symbols();

  print.print_invalid_moves_heading();
  board.move_piece("e3", "e2");
  board.move_piece("e1", "e3");
  board.move_piece("b1", "b2");
  print.print_empty_line();

  print.print_simulated_game_heading();
  board.move_piece("e1", "e2");
  board.move_piece("g8", "h6");
  board.move_piece("b1", "c3");
  board.move_piece("h6", "g8");
  board.move_piece("c3", "d5");
  board.move_piece("g8", "h6");
  board.move_piece("d5", "f6");
  board.move_piece("h6", "g8");
  board.move_piece("f6", "e8");
}
