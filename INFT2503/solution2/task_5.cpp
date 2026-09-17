#include <iostream>

using namespace std;

int main() {
    double number = 0.0;
    double *pointer = &number;
    double &reference = number;

    number = 5;
    cout << "number = " << number << endl;
    *pointer = 10;
    cout << "number = " << number << endl;
    reference = 15;
    cout << "number = " << number << endl;
}
