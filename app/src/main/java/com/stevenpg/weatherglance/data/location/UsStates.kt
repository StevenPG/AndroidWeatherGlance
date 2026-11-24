package com.stevenpg.weatherglance.data.location

/** Maps US state / territory names to USPS codes so locations read "Springfield, IL". */
object UsStates {
    private val codes = mapOf(
        "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
        "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "District of Columbia" to "DC",
        "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL",
        "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA",
        "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN",
        "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
        "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
        "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK", "Oregon" to "OR",
        "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD",
        "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA",
        "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY",
        "Puerto Rico" to "PR", "Guam" to "GU", "U.S. Virgin Islands" to "VI", "American Samoa" to "AS",
        "Northern Mariana Islands" to "MP",
    )

    fun abbreviate(name: String?): String? = name?.let { codes[it] ?: it }
}
