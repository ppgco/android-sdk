package com.pushpushgo.sdk.utils

import android.os.Build
import android.text.TextUtils

class Helper {
    companion object{
        fun getDeviceName(): String? {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) {
                capitalize(model)
            } else capitalize(manufacturer) + " " + model
        }

        private fun capitalize(str: String): String? {
            if (TextUtils.isEmpty(str)) {
                return str
            }
            val arr = str.toCharArray()
            var capitalizeNext = true

            val phrase = StringBuilder()
            for (c in arr) {
                if (capitalizeNext && Character.isLetter(c)) {
                    phrase.append(Character.toUpperCase(c))
                    capitalizeNext = false
                    continue
                } else if (Character.isWhitespace(c)) {
                    capitalizeNext = true
                }
                phrase.append(c)
            }

            return phrase.toString()
        }

    }
}