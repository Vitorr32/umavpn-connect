package com.umavpn.model

/**
 * Which Umamusume variant to find VPN servers for.
 *
 * Global servers live outside Japan, so [GLOBAL] must not apply a JP country filter.
 * Japanese and DMM servers require [countryCode] = "JP".
 */
enum class GameMode(val site: String, val countryCode: String?) {
  GLOBAL("umag", null),
  JAPANESE("uma", "JP"),
  DMM("dmm", "JP"),
}
