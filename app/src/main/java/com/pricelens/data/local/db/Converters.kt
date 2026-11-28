package com.pricelens.data.local.db

import androidx.room.TypeConverter
import com.pricelens.domain.model.UnitPrice
import com.pricelens.domain.model.UnitPriceSource
import com.pricelens.domain.model.WeightUnit
import java.math.BigDecimal

class Converters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)

    @TypeConverter
    fun weightUnitToString(value: WeightUnit?): String? = value?.name

    @TypeConverter
    fun stringToWeightUnit(value: String?): WeightUnit? =
        value?.let { stored -> runCatching { WeightUnit.valueOf(stored) }.getOrNull() }

    @TypeConverter
    fun unitPriceUnitToString(value: UnitPrice.Unit?): String? = value?.name

    @TypeConverter
    fun stringToUnitPriceUnit(value: String?): UnitPrice.Unit? =
        value?.let { stored -> runCatching { UnitPrice.Unit.valueOf(stored) }.getOrNull() }

    @TypeConverter
    fun unitPriceSourceToString(value: UnitPriceSource?): String? = value?.name

    @TypeConverter
    fun stringToUnitPriceSource(value: String?): UnitPriceSource =
        value?.let { stored -> runCatching { UnitPriceSource.valueOf(stored) }.getOrNull() }
            ?: UnitPriceSource.PACKAGE
}
