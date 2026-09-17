#include <iostream>
#include <iomanip>
#include <cmath>

using namespace std;

template <typename Type>
bool equal(Type a, Type b) {
    cout << "Function template was used." << endl;
    return a == b;
}

bool equal(double a, double b) {
    cout << "Template specialization was used." << endl;
    double diff = std::abs(a - b);
    if (diff < 0.00001) {
        return true;
    }
    return false;
}

int main() {
    string a = "Hi!";
    string b = "Hi!";
    bool strings_are_equal = equal(a, b);
    cout << a << " and " << b << " are equal: " << boolalpha << strings_are_equal << endl;

    cout << endl;

    int c = 10;
    int d = 5;
    bool ints_are_equal = equal(c, d);
    cout << c << " and " << d << " are equal: " << boolalpha << ints_are_equal << endl;

    cout << endl;

    // Difference < 0.00001
    double d1 = 10.123456;
    double d2 = 10.123457;
    bool doubles_are_equal = equal(d1, d2);
    cout << std::setprecision(8) << d1 << " and " << d2 << " are equal: " << boolalpha << doubles_are_equal << endl;

    cout << endl;

    // Difference > 0.00001
    double d3 = 10.123456;
    double d4 = 10.123496;
    bool doubles_are_equal_2 = equal(d3, d4);
    cout << std::setprecision(8) << d3 << " and " << d4 << " are equal: " << boolalpha << doubles_are_equal_2 << endl;
}
