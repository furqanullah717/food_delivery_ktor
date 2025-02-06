package com.codewithfk.database

import com.codewithfk.model.Category
import io.ktor.http.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object DatabaseFactory {
    fun init() {
        val driverClassName = "com.mysql.cj.jdbc.Driver"
        val jdbcURL = "jdbc:mysql://localhost:3306/food_delivery"
        val user = "root"
        val password = "root"

        try {
            Class.forName(driverClassName)
            Database.connect(jdbcURL, driverClassName, user, password)

            transaction {
                // Create base tables
                SchemaUtils.createMissingTablesAndColumns(
                    UsersTable,
                    CategoriesTable,
                    RestaurantsTable,
                    MenuItemsTable,
                    AddressesTable,
                    OrdersTable,
                    OrderItemsTable,
                    CartTable,
                    NotificationsTable,
                    RiderLocationsTable,
                    DeliveryRequestsTable
                )

                // Check if rider_id column exists
                val riderIdExists = exec(
                    """
                                SELECT COUNT(*) 
                                FROM information_schema.COLUMNS 
                                WHERE TABLE_SCHEMA = DATABASE()
                                AND TABLE_NAME = 'orders' 
                                AND COLUMN_NAME = 'rider_id'
                            """
                ) { it.next(); it.getInt(1) } ?: 0 > 0

                // Add rider_id column if it doesn't exist
                if (!riderIdExists) {
                    exec(
                        """
                        ALTER TABLE orders 
                        ADD COLUMN rider_id VARCHAR(36) NULL;
                    """
                    )

                    // Add foreign key constraint
                    exec(
                        """
                        ALTER TABLE orders
                        ADD CONSTRAINT fk_orders_rider
                        FOREIGN KEY (rider_id) 
                        REFERENCES users(id);
                    """
                    )
                }
            }
        } catch (e: Exception) {
            println("Database initialization failed: ${e.message}")
            throw e
        }
    }
}

fun Application.migrateDatabase() {
    transaction {
        try {
            // Migration 1: Add FCM token to users
            val fcmTokenExists = exec(
                """
                SELECT COUNT(*) 
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'users' 
                AND COLUMN_NAME = 'fcm_token'
            """
            ) { it.next(); it.getInt(1) } ?: 0 > 0

            if (!fcmTokenExists) {
                exec(
                    """
                    ALTER TABLE users 
                    ADD COLUMN fcm_token VARCHAR(255) NULL
                """
                )
                println("Added fcm_token column to users table")
            }

            // Migration 2: Add category to menu_items
            val categoryExists = exec(
                """
                SELECT COUNT(*) 
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'menu_items' 
                AND COLUMN_NAME = 'category'
            """
            ) { it.next(); it.getInt(1) } ?: 0 > 0

            if (!categoryExists) {
                exec(
                    """
                    ALTER TABLE menu_items 
                    ADD COLUMN category VARCHAR(100) NULL
                """
                )
                println("Added category column to menu_items table")
            }

            // Migration 3: Add isAvailable to menu_items
            val isAvailableExists = exec(
                """
                SELECT COUNT(*) 
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'menu_items' 
                AND COLUMN_NAME = 'is_available'
            """
            ) { it.next(); it.getInt(1) } ?: 0 > 0

            if (!isAvailableExists) {
                exec(
                    """
                    ALTER TABLE menu_items 
                    ADD COLUMN is_available BOOLEAN DEFAULT TRUE
                """
                )
                println("Added is_available column to menu_items table")
            }

            // Migration 4: Update all restaurants to be owned by owner1@example.com
            val owner1Id = UsersTable
                .select { UsersTable.email eq "owner1@example.com" }
                .map { it[UsersTable.id] }
                .firstOrNull()

            if (owner1Id != null) {
                RestaurantsTable.update {
                    it[ownerId] = owner1Id
                }
                println("Updated all restaurants to be owned by owner1@example.com")
            } else {
                println("Warning: owner1@example.com not found, skipping restaurant ownership migration")
            }

            println("All migrations completed successfully")
        } catch (e: Exception) {
            println("Migration failed: ${e.message}")
            throw e
        }
    }
}

fun Application.seedDatabase() {
    environment.monitor.subscribe(ApplicationStarted) {
        transaction {
            // Seed users if none exist
            val owner1Id = if (UsersTable.selectAll().empty()) {
                println("Seeding users...")
                val owner1 = UUID.randomUUID()
                val owner2 = UUID.randomUUID()

                // Insert owner1
                UsersTable.insert {
                    it[id] = owner1
                    it[email] = "owner1@example.com"
                    it[name] = "Restaurant Owner"
                    it[role] = "OWNER"
                    it[authProvider] = "email"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }

                // Insert owner2
                UsersTable.insert {
                    it[id] = owner2
                    it[email] = "owner2@example.com"
                    it[name] = "Another Owner"
                    it[role] = "OWNER"
                    it[authProvider] = "email"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }

                owner1
            } else {
                // Get existing owner1 ID
                UsersTable
                    .select { UsersTable.email eq "owner1@example.com" }
                    .map { it[UsersTable.id] }
                    .firstOrNull() ?: error("owner1@example.com not found")
            }

            // Seed categories if none exist
            val categoryIds = if (CategoriesTable.selectAll().empty()) {
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
                        imageUrl = "https://e7.pngegg.com/pngimages/706/98/png-clipart/japanese-cuisine-chinese-cuisine-vietnamese-cuisine-asian-cuisine-dish-cooking-leaf-vegetable-food.png"
                    ),
                    Category(
                        id = UUID.randomUUID().toString(),
                        name = "Burger",
                        imageUrl = "https://png.pngtree.com/png-vector/20231016/ourmid/pngtree-burger-food-png-free-download-png-image_10199386.png"
                    )
                )

                categories.associate { category ->
                    val uuid = UUID.fromString(category.id)
                    CategoriesTable.insert {
                        it[id] = uuid
                        it[name] = category.name
                        it[imageUrl] = category.imageUrl
                        it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }
                    category.name to uuid
                }
            } else {
                CategoriesTable.selectAll().associate { it[CategoriesTable.name] to it[CategoriesTable.id] }
            }

            // Seed restaurants if none exist
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
                    this[RestaurantsTable.ownerId] = owner1Id
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
            }

            // Seed menu items if none exist
            if (MenuItemsTable.selectAll().empty()) {
                println("Seeding menu items...")
                val restaurants =
                    RestaurantsTable.selectAll().associate { it[RestaurantsTable.name] to it[RestaurantsTable.id] }

                val menuItems = listOf(
                    Pair(
                        "Pizza Palace", listOf(
                            Triple(
                                "Margherita Pizza", "Classic cheese pizza with fresh basil",
                                Pair(12.99, "https://foodbyjonister.com/wp-content/uploads/2020/01/pizzadough18.jpg")
                            ),
                            Triple(
                                "Pepperoni Pizza", "Pepperoni, mozzarella, and marinara sauce",
                                Pair(
                                    14.99,
                                    "https://www.cobsbread.com/us/wp-content//uploads/2022/09/Pepperoni-pizza-850x630-1.png"
                                )
                            ),
                            Triple(
                                "Veggie Supreme", "Loaded with bell peppers, onions, and olives",
                                Pair(
                                    13.99,
                                    "https://www.thecandidcooks.com/wp-content/uploads/2022/07/california-veggie-pizza-feature.jpg"
                                )
                            ),
                            Triple(
                                "Special Pizza", "Classic cheese pizza with fresh basil",
                                Pair(
                                    21.99,
                                    "https://eatlanders.com/wp-content/uploads/2021/05/new-pizza-pic-e1672671486218.jpeg"
                                )
                            ),
                            Triple(
                                "Crown Crust Pizza", "Pepperoni, mozzarella, and marinara sauce",
                                Pair(19.99, "https://wenewsenglish.pk/wp-content/uploads/2024/05/Recipe-1.jpg")
                            ),
                            Triple(
                                "Thin Crust Supreme", "Loaded with bell peppers, onions, and olives",
                                Pair(
                                    18.99,
                                    "https://cdn.apartmenttherapy.info/image/upload/f_jpg,q_auto:eco,c_fill,g_auto,w_1500,ar_4:3/k%2Farchive%2Fcb2e9502cd9da3468caa944e15527b19bce68a8e"
                                )
                            ),
                            Triple(
                                "Malai Boti Pizza", "Classic cheese pizza with fresh basil",
                                Pair(
                                    14.99,
                                    "https://www.tastekahani.com/wp-content/uploads/2022/05/71.Malai-Boti-Pizza.jpg"
                                )
                            ),
                            Triple(
                                "Tikka Pizza", "Pepperoni, mozzarella, and marinara sauce",
                                Pair(
                                    16.99,
                                    "https://onestophalal.com/cdn/shop/articles/tikka_masala_pizza-1694014914105_1200x.jpg?v=1694568363"
                                )
                            ),
                            Triple(
                                "Cheeze Crust Supreme", "Loaded with bell peppers, onions, and olives",
                                Pair(
                                    17.99,
                                    "https://www.allrecipes.com/thmb/ofh4mVETQPBbcOb4uCFQr92cqb4=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/2612551-cheesy-crust-skillet-pizza-The-Gruntled-Gourmand-1x1-1-f9a328af9dfe487a9fc408f581927696.jpg"
                                )
                            )
                        )
                    ),
                    Pair(
                        "Burger Haven", listOf(
                            Triple(
                                "Classic Cheeseburger", "Juicy beef patty with cheddar cheese",
                                Pair(
                                    10.99,
                                    "https://rhubarbandcod.com/wp-content/uploads/2022/06/The-Classic-Cheeseburger-1.jpg"
                                )
                            ),
                            Triple(
                                "Veggie Burger", "Grilled veggie patty with avocado",
                                Pair(
                                    9.99,
                                    "https://www.foodandwine.com/thmb/pwFie7NRkq4SXMDJU6QKnUKlaoI=/1500x0/filters:no_upscale():max_bytes(150000):strip_icc()/Ultimate-Veggie-Burgers-FT-Recipe-0821-5d7532c53a924a7298d2175cf1d4219f.jpg"
                                )
                            )
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
                    this[MenuItemsTable.arModelUrl] = null
                    this[MenuItemsTable.category] = null // New field
                    this[MenuItemsTable.isAvailable] = true // New field
                    this[MenuItemsTable.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                }

                println("Menu items seeded for all restaurants.")
            }
        }
    }
}