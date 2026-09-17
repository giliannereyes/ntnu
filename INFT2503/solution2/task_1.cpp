#include <iostream>

using namespace std;

void print_variables(int &i, int &j, int *&p, int *&q) {
    cout << "i: address = " << &i << ", content = " << i << endl;
    cout << "j: address = " << &j << ", content = " << j << endl;
    cout << "p: address = " << &p << ", content = " << p << ", points to = " << *p << endl;
    cout << "q: address = " << &q << ", content = " << q << ", points to = " << *q << endl;
}

int main() {
    int i = 3;
    int j = 5;
    int *p = &i;
    int *q = &j;

    cout << "Before part b:" << endl;
    print_variables(i, j, p, q);

    *p = 7; // i = 7
    *q += 4; // j = 9
    *q = *p + 1; // j = 8
    p = q; // p points to j

    cout << endl << "Output from part b:" << endl;
    cout << *p << " " << *q << endl;

    cout << endl << "After part b:" << endl;
    print_variables(i, j, p, q);
}
