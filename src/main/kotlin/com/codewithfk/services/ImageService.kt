package com.codewithfk.services

import com.codewithfk.configs.SupabaseConfig
import com.codewithfk.configs.SupabaseConfig.STORAGE_BUCKET
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*
import java.util.*
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

object ImageService {
    private val client = HttpClient(CIO)
    private const val STORAGE_URL = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object"
    private const val TARGET_SIZE_KB = 100
    private const val MAX_DIMENSION = 1024

    suspend fun uploadImage(multipart: MultiPartData, folder: String): String {
        try {
            val imageData = multipart.readAllParts().first { it is PartData.FileItem }
            val originalBytes = (imageData as PartData.FileItem).streamProvider().readBytes()
            
            // Compress image
            val compressedBytes = compressImage(originalBytes)
            
            // Get file extension from original filename
            val originalFileName = imageData.originalFileName ?: "image.jpg"
            val fileExtension = originalFileName.substringAfterLast(".", "jpg")
            
            // Generate new filename with timestamp and random UUID
            val timestamp = System.currentTimeMillis()
            val randomUUID = UUID.randomUUID().toString().take(8)
            val newFileName = "${folder}/image_${timestamp}_${randomUUID}.$fileExtension"
            
            // Upload to Supabase Storage
            val response = client.put("$STORAGE_URL/${SupabaseConfig.STORAGE_BUCKET}/$newFileName") {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                    append("Content-Type", "image/jpeg")
                }
                setBody(compressedBytes)
            }

            if (response.status.isSuccess()) {
                return "$STORAGE_URL/public/${SupabaseConfig.STORAGE_BUCKET}/$newFileName"
            } else {
                throw IllegalStateException("Failed to upload image: ${response.status}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to upload image: ${e.message}")
        }
    }

    private fun compressImage(imageBytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(imageBytes)
        val originalImage = ImageIO.read(inputStream)
        
        // Scale image if needed
        val scaledImage = if (originalImage.width > MAX_DIMENSION || originalImage.height > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(originalImage.width, originalImage.height)
            val newWidth = (originalImage.width * scale).roundToInt()
            val newHeight = (originalImage.height * scale).roundToInt()
            
            val scaledImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = scaledImage.createGraphics()
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null)
            g2d.dispose()
            scaledImage
        } else originalImage

        // Compress with different quality settings until target size is reached
        var quality = 1.0f
        var outputBytes: ByteArray
        val outputStream = ByteArrayOutputStream()
        
        do {
            outputStream.reset()
            val iter = ImageIO.getImageWritersByFormatName("jpeg").next()
            val writeParam = iter.defaultWriteParam
            writeParam.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            writeParam.compressionQuality = quality
            
            val ios = ImageIO.createImageOutputStream(outputStream)
            iter.output = ios
            iter.write(null, javax.imageio.IIOImage(scaledImage, null, null), writeParam)
            iter.dispose()
            ios.close()
            
            outputBytes = outputStream.toByteArray()
            quality -= 0.1f
        } while (outputBytes.size > TARGET_SIZE_KB * 1024 && quality > 0.1f)

        return outputBytes
    }

    suspend fun deleteImage(imageUrl: String) {
        try {
            val fileName = imageUrl.substringAfterLast("/${STORAGE_BUCKET}/")
            
            val response = client.delete("$STORAGE_URL/${SupabaseConfig.STORAGE_BUCKET}/$fileName") {
                headers {
                    append("apikey", SupabaseConfig.SUPABASE_KEY)
                    append("Authorization", "Bearer ${SupabaseConfig.SUPABASE_KEY}")
                }
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("Failed to delete image: ${response.status}")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to delete image: ${e.message}")
        }
    }
} 