// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.onurcvnoglu.asyaextra.dizikorea

import com.fasterxml.jackson.annotation.JsonProperty

data class KoreaSearch(
    @JsonProperty("theme") val theme: String
)