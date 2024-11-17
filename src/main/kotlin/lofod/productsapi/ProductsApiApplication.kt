package lofod.productsapi

import lofod.productsapi.security.SecurityConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import


@SpringBootApplication
@Import(value = [SecurityConfig::class])
@EnableConfigurationProperties
class ProductsApiApplication

fun main(args: Array<String>) {
    runApplication<ProductsApiApplication>(*args)
}
