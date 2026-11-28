package com.pricelens.testing

import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.WeightUnit

/**
 * Эталоны подтверждённых пользователем ценников 1.jpg–13.jpg.
 * Используются и в JVM-тестах на сырых токенах, и в инструментальном тесте на реальных JPEG.
 */
data class RealPriceTagCase(
    val id: String,
    val name: String,
    val amount: String,
    val amountLabel: String? = null,
    val packageUnit: WeightUnit = WeightUnit.GRAM,
    val price: String,
    val priceByCard: String? = null,
    val unitPrice: String,
    val unitPriceUnit: UnitPrice.Unit = UnitPrice.Unit.KILOGRAM,
)

object RealPriceTagCases {
    val all: List<RealPriceTagCase> = listOf(
        RealPriceTagCase(
            id = "1.jpg",
            name = "Лапша «Биг Ланч» бургиньон с говядиной и лесными грибами",
            amount = "65",
            price = "49.99",
            unitPrice = "769.08",
        ),
        RealPriceTagCase(
            id = "2.jpg",
            name = "Ананас",
            amount = "1000",
            price = "199.99",
            unitPrice = "199.99",
        ),
        RealPriceTagCase(
            id = "3.jpg",
            name = "Консервы рыбные шпроты в масле",
            amount = "240",
            amountLabel = "230-240 г",
            price = "139.99",
            unitPrice = "583.29",
        ),
        RealPriceTagCase(
            id = "4.jpg",
            name = "Консервы рыбные тунец «За Родину» с киноа и травами, ж/б",
            amount = "160",
            price = "119.99",
            unitPrice = "749.94",
        ),
        RealPriceTagCase(
            id = "5.jpg",
            name = "Паштет «В Три Приёма» со вкусом копчёная индейка",
            amount = "70",
            price = "24.99",
            unitPrice = "357.00",
        ),
        RealPriceTagCase(
            id = "6.jpg",
            name = "Паштет «В Три Приёма» со вкусом бекона",
            amount = "70",
            price = "24.99",
            unitPrice = "357.00",
        ),
        RealPriceTagCase(
            id = "7.jpg",
            name = "Паштет «Хангров» нежный из индюшачьего мяса",
            amount = "90",
            price = "49.99",
            unitPrice = "555.44",
        ),
        RealPriceTagCase(
            id = "8.jpg",
            name = "ПЕЧЕНЬЕ ЗАТЯЖНОЕ «ИМБИРНОЕ»",
            amount = "190",
            price = "144",
            unitPrice = "757.89",
        ),
        RealPriceTagCase(
            id = "9.jpg",
            name = "ПЕЧЕНЬЕ ОВСЯНОЕ «СУПЕРЗЛАК»",
            amount = "230",
            price = "126",
            unitPrice = "547.83",
        ),
        RealPriceTagCase(
            id = "10.jpg",
            name = "ПЕЧЕНЬЕ «ОВСЯНОЕ С клюквой, БРУСНИКОЙ И СЕМЕНАМИ ТЫКВЫ»",
            amount = "200",
            price = "262",
            unitPrice = "1310.00",
        ),
        RealPriceTagCase(
            id = "11.jpg",
            name = "ЗЕФИР ВАНИЛЬНЫЙ",
            amount = "80",
            price = "64",
            unitPrice = "800.00",
        ),
        RealPriceTagCase(
            id = "12.jpg",
            name = "ЗЕФИР СЛИВОВЫЙ",
            amount = "150",
            price = "155",
            unitPrice = "1033.33",
        ),
        RealPriceTagCase(
            id = "13.jpg",
            name = "ПЕЧЕНЬЕ ОВСЯНОЕ С ЧЕРНОСЛИВОМ",
            amount = "200",
            price = "270",
            priceByCard = "243",
            unitPrice = "1215.00",
        ),
    )
}
