package lofod.productsapi.model.request

import lofod.productsapi.model.CustomFieldType

data class CustomFieldDefinitionDto(
    val fieldId: String? = null,
    val title: String,
    val type: CustomFieldType,
)
