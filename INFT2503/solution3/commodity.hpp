#pragma once

#include <string>

using namespace std;

const double sales_tax = 1.25;

class Commodity {
public:
    Commodity(const string &name_, int id, double price);

    const string &get_name() const { return name; }
    int get_id() const { return id; }

    double get_price() const { return price; }
    double get_price(double quantity) const;

    double get_price_with_sales_tax() const { return get_price() * sales_tax; }
    double get_price_with_sales_tax(double quantity) const;

    void set_price(double price) { this->price = price; }

private:
    string name;
    int id;
    double price;
};