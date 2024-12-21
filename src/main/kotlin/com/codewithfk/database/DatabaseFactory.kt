package com.codewithfk.database


import com.codewithfk.model.Category
import io.ktor.http.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.text.insert

object DatabaseFactory {
    fun init() {
        val dbUrl = "jdbc:mysql://localhost:3306/food_delivery"
        val dbUser = "root"
        val dbPassword = "root"

        Database.connect(
            url = dbUrl,
            driver = "com.mysql.cj.jdbc.Driver",
            user = dbUser,
            password = dbPassword
        )

        transaction {
            SchemaUtils.create(
                UsersTable,
                CategoriesTable,
                RestaurantsTable,
                MenuItemsTable,
                OrdersTable,
            )
        }

    }
}


fun Application.seedDatabase() {
    environment.monitor.subscribe(ApplicationStarted) {
        transaction {
            // Seed users
            if (UsersTable.selectAll().empty()) {
                println("Seeding users...")
                val users = listOf(
                    Triple(UUID.randomUUID(), "owner1@example.com", "Restaurant Owner"),
                    Triple(UUID.randomUUID(), "owner2@example.com", "Another Owner")
                )

                users.forEach { user ->
                    UsersTable.insert {
                        it[id] = user.first
                        it[email] = user.second
                        it[name] = user.third
                        it[passwordHash] = "hashed-password" // Placeholder for hashed password
                        it[role] = "owner"
                        it[authProvider] = "email"
                    }
                }

                println("Users seeded: ${users.map { it.second }}")
            } else {
                println("Users already exist.")
            }

            // Fetch user IDs
            val userIds = UsersTable.selectAll().map { it[UsersTable.id] }

            // Seed categories
            if (CategoriesTable.selectAll().empty()) {
                println("Seeding categories...")
                val categories = listOf(
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Pizza",
                        imageUrl = "https://images.vexels.com/content/136312/preview/logo-pizza-fast-food-d65bfe.png"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Fast Food",
                        imageUrl = "https://www.pngarts.com/files/3/Fast-Food-Free-PNG-Image.png"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Beverages",
                        imageUrl = "https://www.pngfind.com/pngs/m/172-1729150_alcohol-drinks-png-mojito-drink-transparent-png.png"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Desserts",
                        imageUrl = "https://img.freepik.com/premium-psd/isolated-cake-style-png-with-white-background-generative-ia_209190-251177.jpg"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Healthy Food",
                        imageUrl = "https://png.pngtree.com/png-clipart/20190516/original/pngtree-healthy-food-png-image_3776802.jpg"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Asian Cuisine",
                        imageUrl = "https://e7.pngegg.com/pngimages/706/98/png-clipart-japanese-cuisine-chinese-cuisine-vietnamese-cuisine-asian-cuisine-dish-cooking-leaf-vegetable-food.png"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Burger",
                        imageUrl = "https://png.pngtree.com/png-vector/20231016/ourmid/pngtree-burger-food-png-free-download-png-image_10199386.png"
                    ),
                )
                CategoriesTable.batchInsert(categories) { category ->
                    this[CategoriesTable.id] = UUID.fromString(category.id)
                    this[CategoriesTable.name] = category.name
                    this[CategoriesTable.imageUrl] = category.imageUrl ?: ""

                }

                println("Categories seeded: ${categories.map { it.name }}")
            } else {
                println("Categories already exist.")
            }

            // Get seeded category IDs
            val categoryIds =
                CategoriesTable.selectAll().associate { it[CategoriesTable.name] to it[CategoriesTable.id] }

            // Seed restaurants
            if (RestaurantsTable.selectAll().empty()) {
                println("Seeding restaurants...")
                val restaurants = listOf(
                    Triple(
                        Pair(
                            "Pizza Palace",
                            "https://www.marthastewart.com/thmb/3N-0cJgJfLDyytnCehJd4aVgHJw=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/white-pizza-172-d112100_horiz-c868dcf28ed44b21af90f11797d6d7d6.jpgitokKoRSmCVm"
                        ),
                        "123 Main St, New York, NY",
                        Triple(40.712776, -74.005978, "Pizza")
                    ),
                    Triple(
                        Pair(
                            "Burger Haven",
                            "https://imageproxy.wolt.com/mes-image/43bb7be3-03c2-4337-9d52-99cba2b1650d/85493202-0013-44f0-b7c1-59262d53e9ff"
                        ),
                        "456 Elm St, Los Angeles, CA",
                        Triple(40.712776, -74.005979, "Fast Food")
                    ),
                    Triple(
                        Pair(
                            "Dessert Delight",
                            "https://static.vecteezy.com/system/resources/previews/032/160/853/large_2x/mouthwatering-dessert-heaven-a-tray-of-assorted-creamy-delights-ai-generated-photo.jpg"
                        ),
                        "789 Pine St, Chicago, IL",
                        Triple(40.712776, -74.005973, "Desserts")
                    ),
                    Triple(
                        Pair(
                            "Healthy Bites",
                            "https://i2.wp.com/www.downshiftology.com/wp-content/uploads/2019/04/Cobb-Salad-main.jpg"
                        ),
                        "321 Oak St, Miami, FL",
                        Triple(40.712776, -74.005974, "Healthy Food")
                    ),
                    Triple(
                        Pair(
                            "Sushi Express",
                            "https://tb-static.uber.com/prod/image-proc/processed_images/87baf961b666795ea98160dc3b1d465c/fb86662148be855d931b37d6c1e5fcbe.jpeg"
                        ),
                        "654 Maple St, Seattle, WA",
                        Triple(40.712776, -74.005976, "Asian Cuisine")
                    ),
                    Triple(
                        Pair(
                            "Coffee Corner",
                            "https://insanelygoodrecipes.com/wp-content/uploads/2020/07/Cup-Of-Creamy-Coffee.png"
                        ),
                        "987 Cedar St, San Francisco, CA",
                        Triple(40.712776, -74.005977, "Beverages")
                    )
                )

                RestaurantsTable.batchInsert(restaurants) { restaurant ->
                    this[RestaurantsTable.id] = UUID.randomUUID()
                    this[RestaurantsTable.ownerId] =
                        userIds.randomOrNull() ?: error("No users found to assign as owner.")
                    this[RestaurantsTable.name] = restaurant.first.first
                    this[RestaurantsTable.address] = restaurant.second
                    this[RestaurantsTable.latitude] = restaurant.third.first
                    this[RestaurantsTable.longitude] = restaurant.third.second
                    this[RestaurantsTable.imageUrl] = restaurant.first.second
                    this[RestaurantsTable.categoryId] =
                        categoryIds[restaurant.third.third] ?: error("Category not found: ${restaurant.third.third}")
                    this[RestaurantsTable.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                }

                println("Restaurants seeded: ${restaurants.map { it.first }}")
            } else {
                println("Restaurants already exist.")
            }

            if (MenuItemsTable.selectAll().empty()) {
                println("Seeding menu items...")

                val restaurants =
                    RestaurantsTable.selectAll().associate { it[RestaurantsTable.name] to it[RestaurantsTable.id] }

                val menuItems = listOf(
                    Pair(
                        "Pizza Palace", listOf(
                            Triple("Margherita Pizza", "Classic cheese pizza with fresh basil", Pair(12.99, "https://foodbyjonister.com/wp-content/uploads/2020/01/pizzadough18.jpg")),
                            Triple("Pepperoni Pizza", "Pepperoni, mozzarella, and marinara sauce", Pair(14.99, "https://www.cobsbread.com/us/wp-content//uploads/2022/09/Pepperoni-pizza-850x630-1.png")),
                            Triple("Veggie Supreme", "Loaded with bell peppers, onions, and olives", Pair(13.99, "https://www.thecandidcooks.com/wp-content/uploads/2022/07/california-veggie-pizza-feature.jpg")),
                            Triple("Special Pizza", "Classic cheese pizza with fresh basil", Pair(21.99, "https://eatlanders.com/wp-content/uploads/2021/05/new-pizza-pic-e1672671486218.jpeg")),
                            Triple("Crown Crust Pizza", "Pepperoni, mozzarella, and marinara sauce", Pair(19.99, "https://wenewsenglish.pk/wp-content/uploads/2024/05/Recipe-1.jpg")),
                            Triple(
                                "Thin Crust Supreme",
                                "Loaded with bell peppers, onions, and olives",
                                Pair(18.99, "https://cdn.apartmenttherapy.info/image/upload/f_jpg,q_auto:eco,c_fill,g_auto,w_1500,ar_4:3/k%2Farchive%2Fcb2e9502cd9da3468caa944e15527b19bce68a8e")
                            ),
                            Triple("Malai Boti Pizza", "Classic cheese pizza with fresh basil", Pair(14.99, "https://www.tastekahani.com/wp-content/uploads/2022/05/71.Malai-Boti-Pizza.jpg")),
                            Triple("Tikka Pizza", "Pepperoni, mozzarella, and marinara sauce", Pair(16.99, "https://onestophalal.com/cdn/shop/articles/tikka_masala_pizza-1694014914105_1200x.jpg?v=1694568363")),
                            Triple(
                                "Cheeze Crust Supreme",
                                "Loaded with bell peppers, onions, and olives",
                                Pair(17.99, "https://www.allrecipes.com/thmb/ofh4mVETQPBbcOb4uCFQr92cqb4=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/2612551-cheesy-crust-skillet-pizza-The-Gruntled-Gourmand-1x1-1-f9a328af9dfe487a9fc408f581927696.jpg")
                            )
                        )
                    ),
                    Pair(
                        "Burger Haven", listOf(
                            Triple("Classic Cheeseburger", "Juicy beef patty with cheddar cheese", Pair(10.99, "https://rhubarbandcod.com/wp-content/uploads/2022/06/The-Classic-Cheeseburger-1.jpg")),
                            Triple("Veggie Burger", "Grilled veggie patty with avocado", Pair(9.99, "https://www.foodandwine.com/thmb/pwFie7NRkq4SXMDJU6QKnUKlaoI=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/Ultimate-Veggie-Burgers-FT-Recipe-0821-5d7532c53a924a7298d2175cf1d4219f.jpg"))
                        )
                    )
                )

                MenuItemsTable.batchInsert(menuItems.flatMap { (restaurantName, items) ->
                    val restaurantId = restaurants[restaurantName] ?: error("Restaurant not found: $restaurantName")
                    items.map { menuItem ->
                        Triple(restaurantId, menuItem.first, menuItem.second to menuItem.third)
                    }
                }) { menuItem ->
                    this[MenuItemsTable.id] = UUID.randomUUID()
                    this[MenuItemsTable.restaurantId] = menuItem.first
                    this[MenuItemsTable.name] = menuItem.second
                    this[MenuItemsTable.description] = menuItem.third.first
                    this[MenuItemsTable.price] = menuItem.third.second.first
                    this[MenuItemsTable.imageUrl] = menuItem.third.second.second
                    this[MenuItemsTable.arModelUrl] = null // Add if you want AR URLs
                    this[MenuItemsTable.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                }

                println("Menu items seeded for all restaurants.")
            } else {
                println("Menu items already exist.")
            }
        }
    }
}