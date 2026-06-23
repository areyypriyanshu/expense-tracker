package com.expensetracker.domain.engine

import com.expensetracker.data.model.Category

class CategoryEngine {
    fun autoCategorize(note: String, categories: List<Category>): String {
        if (note.isBlank()) return "Other"
        
        val boundedNote = note.take(500).lowercase()
        
        for (category in categories) {
            val keywords = category.keywords.take(500).split(",").map { it.trim().lowercase().take(50) }
            for (keyword in keywords) {
                if (keyword.isNotEmpty() && boundedNote.contains(keyword)) {
                    return category.name
                }
            }
        }
        
        return "Other"
    }

    fun getCategoryIcon(categoryName: String, categories: List<Category>): String {
        val boundedName = categoryName.take(50)
        return categories.find { it.name == boundedName }?.icon ?: "more_horiz"
    }
}
