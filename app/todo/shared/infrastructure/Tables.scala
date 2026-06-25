package todo.shared.infrastructure

import todo.user.infrastructure.UsersTable
import todo.category.infrastructure.CategoriesTable
import todo.task.infrastructure.{TasksTable, TaskItemCategoriesTable}

trait Tables
    extends UsersTable
    with CategoriesTable
    with TasksTable
    with TaskItemCategoriesTable
