#include "set.hpp"

using namespace std;

Set::Set() = default;

Set Set::operator+(const Set &other) const {
    Set union_set = *this;
    for (int number: other.numbers) {
        union_set += number;
    }
    return union_set;
}

Set &Set::operator+=(int number) {
    for (int existing_number : numbers) {
        if (existing_number == number) {
            return *this;
        }
    }
    numbers.push_back(number);
    return *this;
}

std::ostream &operator<<(std::ostream &out, const Set &set) {
    out << "{ ";
    for (int number: set.numbers) {
        out << number << " ";
    }
    out << "}";
    return out;
}

