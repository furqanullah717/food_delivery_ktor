package com.codewithfk.services

import com.codewithfk.database.AddressesTable
import com.codewithfk.model.Address
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object AddressService {

    fun addAddress(address: Address): UUID {
        return transaction {
            AddressesTable.insert {
                it[userId] = UUID.fromString(address.userId)
                it[addressLine1] = address.addressLine1
                it[addressLine2] = address.addressLine2
                it[city] = address.city
                it[state] = address.state
                it[zipCode] = address.zipCode
                it[country] = address.country
                it[latitude] = address.latitude
                it[longitude] = address.longitude
            } get AddressesTable.id
        }
    }

    fun getAddressesByUser(userId: UUID): List<Address> {
        return transaction {
            AddressesTable.select { AddressesTable.userId eq userId }
                .map {
                    Address(
                        id = it[AddressesTable.id].toString(),
                        userId = it[AddressesTable.userId].toString(),
                        addressLine1 = it[AddressesTable.addressLine1],
                        addressLine2 = it[AddressesTable.addressLine2],
                        city = it[AddressesTable.city],
                        state = it[AddressesTable.state],
                        zipCode = it[AddressesTable.zipCode],
                        country = it[AddressesTable.country],
                        latitude = it[AddressesTable.latitude],
                        longitude = it[AddressesTable.longitude]
                    )
                }
        }
    }

    fun updateAddress(addressId: UUID, updatedAddress: Address): Boolean {
        return transaction {
            AddressesTable.update({ AddressesTable.id eq addressId }) {
                it[addressLine1] = updatedAddress.addressLine1
                it[addressLine2] = updatedAddress.addressLine2
                it[city] = updatedAddress.city
                it[state] = updatedAddress.state
                it[zipCode] = updatedAddress.zipCode
                it[country] = updatedAddress.country
                it[latitude] = updatedAddress.latitude
                it[longitude] = updatedAddress.longitude
            } > 0
        }
    }

    fun deleteAddress(addressId: UUID): Boolean {
        return transaction {
            AddressesTable.deleteWhere { AddressesTable.id eq addressId } > 0
        }
    }

    fun getAddressById(addressId: UUID): Address? {
        return transaction {
            AddressesTable.select { AddressesTable.id eq addressId }
                .map {
                    Address(
                        id = it[AddressesTable.id].toString(),
                        userId = it[AddressesTable.userId].toString(),
                        addressLine1 = it[AddressesTable.addressLine1],
                        addressLine2 = it[AddressesTable.addressLine2],
                        city = it[AddressesTable.city],
                        state = it[AddressesTable.state],
                        zipCode = it[AddressesTable.zipCode],
                        country = it[AddressesTable.country],
                        latitude = it[AddressesTable.latitude],
                        longitude = it[AddressesTable.longitude]
                    )
                }.singleOrNull()
        }
    }

    fun createDefaultAddress(userId: UUID) {
        transaction {
            AddressesTable.insert {
                it[AddressesTable.userId] = (userId)
                it[addressLine1] = "1600 Amphitheatre Parkway"
                it[city] = "Mountain View"
                it[state] = "CA"
                it[zipCode] = "94043"
                it[country] = "US"
                it[latitude] = 37.422102
                it[longitude] = -122.084153
            } get AddressesTable.id
        }
    }
} 