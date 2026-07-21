cmake_minimum_required(VERSION 3.22.1)

project(quietpdf_merge)

add_library(quietpdf_merge SHARED pdf_merge_bridge.cpp)

find_library(dl_library dl)
target_link_libraries(quietpdf_merge ${dl_library})
