template <typename Type1, typename Type2>
class Pair {
public:
    Type1 first;
    Type2 second;

    Pair(const Type1 &first, const Type2 &second) : first(first), second(second) {}

    Pair operator+(const Pair &other) const {
        return Pair(first + other.first, second + other.second);
    }

    bool operator>(const Pair &other) const {
        return (first + second) > (other.first + other.second);
    }
};