package ch.hubisan.sharetoemail.data

data class Recipient(
    val id: String,     // stable id (UUID string)
    val label: String,  // shown in picker
    val email: String
)