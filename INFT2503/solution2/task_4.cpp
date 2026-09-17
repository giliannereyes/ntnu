#include <iostream>

using namespace std;

int main() {
    int a = 5;

    // int &b;
    // Feil: En referanse må initialiseres når den deklareres. Den må alltid referere til en eksisterende variabel.
    int &b = a;

    int *c;

    c = &b;

    // *a = *b + *c;
    // Feil: a er en int, ikke en peker, så *a er ugyldig.
    // Feil: b er en int-referanse, ikke en peker, så *b er også ugyldig.
    a = b + *c;

    // &b = 2;
    // Feil: &b er adressen til b. Man kan ikke tilordne en int-verdi til en adresse.
    // For å endre verdien b refererer til, skriver vi b = 2.
    b = 2;

    cout << "a = " << a << endl;
    cout << "b = " << b << endl;
    cout << "*c = " << *c << endl;
}
