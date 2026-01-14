package com.example.taptopayandroid.models

data class Cart(
    val id: String,
    val items: List<LineItem>,
    val total: Long,
    val currency_code: String
)

data class LineItem(
    val id: String,
    val title: String,
    val quantity: Int,
    val unit_price: Long,
    val thumbnail: String?
)
