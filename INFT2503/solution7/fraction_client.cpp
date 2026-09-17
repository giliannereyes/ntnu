#include "fraction.hpp"
#include <iostream>
#include <string>

using namespace std;

void print(const string &text, const Fraction &broek) {
    cout << text << broek.numerator << " / " << broek.denominator << endl;
}

int main() {
    Fraction a(10, 20);
    Fraction b(3, 4);
    Fraction c;
    c.set(5);
    Fraction d = a / b;

    print("a = ", a);
    print("b = ", b);
    print("c = ", c);
    print("d = ", d);

    b += a;
    ++c;
    d *= d;

    print("b = ", b);
    print("c = ", c);
    print("d = ", d);

    c = a + b - d * a;
    c = -c;

    print("c = ", c);

    if (a + b != c + d)
        cout << "a + b != c + d" << endl;
    else
        cout << " a + b == c + d" << endl;
    while (b > a)
        b -= a;
    print("b = ", b);

    // oppgave 1a
    cout << endl;
    Fraction fraction1(1,3);
    print("fraction1 = ", fraction1);
    print("fraction1 - 5 = ", fraction1 - 5);
    print("5 - fraction1 = ", 5 - fraction1);
    cout << endl;

    // oppgave 1b
    Fraction fraction2(2,5);
    print("fraction2 = ", fraction2);
    print("5 - 3 - fraction1 - 7 - fraction2 = ", 5 - 3 - fraction1 - 7 - fraction2);

    // Teorisvar: Uttrykket tolkes fra venstre mot hoyre:
    // (((5 - 3) - fraction1) - 7) - fraction2.
    // Først brukes vanlig int-minus, deretter operator-(int, Fraction),
    // så Fraction::operator-(int), og til slutt Fraction::operator-(Fraction).


}
