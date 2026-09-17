cmake_minimum_required(VERSION 4.0)
project(solution4)

set(CMAKE_CXX_STANDARD 20)

add_executable(task1 task_1.cpp)
add_executable(task2 task_2.cpp)

find_package(PkgConfig REQUIRED)
pkg_check_modules(GTKMM REQUIRED IMPORTED_TARGET gtkmm-3.0)

target_link_libraries(task2 PRIVATE PkgConfig::GTKMM)
