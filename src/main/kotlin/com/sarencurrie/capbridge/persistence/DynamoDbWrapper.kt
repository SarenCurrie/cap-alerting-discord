package com.sarencurrie.capbridge.persistence

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.model.*


class DynamoDbWrapper {
    private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(System.getenv("DYNAMODB_ENDPOINT"), "us-west-2"))
        .build()

    private var table: Table? = null

    private val dynamoDB = DynamoDB(client)

    fun createTable(): Table {
        val tableName = "AlertsSent"

        val tableReference = dynamoDB.getTable(tableName)
        try {
            tableReference.describe()
            this.table = tableReference
            return tableReference
        } catch (e: ResourceNotFoundException) {
            println("Table does not exist, creating...")
        }

        val table: Table = dynamoDB.createTable(
            tableName,
            listOf(
                KeySchemaElement("id", KeyType.HASH),  // Partition
            ),
            listOf(
                AttributeDefinition("id", ScalarAttributeType.S),
            ),
            ProvisionedThroughput(10L, 10L)
        )
        table.waitForActive()
        println("Success.  Table status: " + table.description.tableStatus)
        this.table = table
        return table
    }

    fun hasSent(id: String): Boolean {
        if (table == null) {
            createTable()
        }
        val foo = table!!
        val item: Item? = foo.getItem("id", id)
        return item != null
    }

    fun store(id: String) {
        if (table == null) {
            createTable()
        }
        val item = Item().withPrimaryKey("id", id)//.with("date", Date())
        table!!.putItem(item)
    }
}