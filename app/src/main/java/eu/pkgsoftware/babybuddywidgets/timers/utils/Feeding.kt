package eu.pkgsoftware.babybuddywidgets.timers.utils

import eu.pkgsoftware.babybuddywidgets.Constants.FeedingMethodEnum
import eu.pkgsoftware.babybuddywidgets.Constants.FeedingTypeEnum
import eu.pkgsoftware.babybuddywidgets.R

fun feedingImageResourceFor(
    selectedType: FeedingTypeEnum?,
    selectedMethod: FeedingMethodEnum?,
) = when (selectedType) {
    FeedingTypeEnum.BREAST_MILK -> {
        when (selectedMethod) {
            FeedingMethodEnum.LEFT_BREAST -> R.drawable.pkg_breast_left
            FeedingMethodEnum.RIGHT_BREAST -> R.drawable.pkg_breast_right

            FeedingMethodEnum.BOTH_BREASTS,
            null,
                -> R.drawable.pkg_breast

            FeedingMethodEnum.BOTTLE,
            FeedingMethodEnum.PARENT_FED,
            FeedingMethodEnum.SELF_FED,
                -> R.drawable.pkg_bottle
        }
    }

    FeedingTypeEnum.SOLID_FOOD -> R.drawable.pkg_solid_food

    FeedingTypeEnum.FORMULA,
    FeedingTypeEnum.FORTIFIED_BREAST_MILK,
    null,
        -> R.drawable.pkg_bottle
}