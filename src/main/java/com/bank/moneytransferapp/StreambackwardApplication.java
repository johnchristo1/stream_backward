package com.bank.moneytransferapp;

import java.time.Instant;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Serialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootApplication
public class StreambackwardApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreambackwardApplication.class, args);

		Properties config = new Properties();

		config.put(StreamsConfig.APPLICATION_ID_CONFIG, "bank-Transaction-backword");
		config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		// we disable the cache to demonstrate all the "steps" involved in the
		// transformation - not recommended in prod
		config.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "0");

		// Exactly once processing!!
		config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE);

		// json Serde
		final Serializer<JsonNode> jsonSerializer = new JsonSerializer();
		final Deserializer<JsonNode> jsonDeserializer = new JsonDeserializer();
		final Serde<JsonNode> jsonSerde = Serdes.serdeFrom(jsonSerializer, jsonDeserializer);

		StreamsBuilder builder = new StreamsBuilder();

		KStream<String, JsonNode> bankTransactions = builder.stream("bank3", Consumed.with(Serdes.String(), jsonSerde));

		// create the initial json object for balances
		ObjectNode initialBalanceb = JsonNodeFactory.instance.objectNode();
		initialBalanceb.put("SenderAccountnumber", 0);
		initialBalanceb.put("ReceiverAccountnumber", 0);
		initialBalanceb.put("Report", 0);
		initialBalanceb.put("Amountb", 0);

		initialBalanceb.put("time", Instant.ofEpochMilli(0L).toString());

		KTable<String, JsonNode> bankBalance = bankTransactions.groupByKey(Serialized.with(Serdes.String(), jsonSerde))
				.aggregate(() -> initialBalanceb, (key, transactionr, Report) -> newBalanceb(transactionr, Report),
						Materialized.<String, JsonNode, KeyValueStore<Bytes, byte[]>>as("bank-balance-agg")
								.withKeySerde(Serdes.String()).withValueSerde(jsonSerde));

		bankBalance.toStream().to("bank4", Produced.with(Serdes.String(), jsonSerde));

		KafkaStreams streams = new KafkaStreams(builder.build(), config);
		streams.cleanUp();
		streams.start();

		// print the topology
		streams.localThreadsMetadata().forEach(data -> System.out.println(data));

		// shutdown hook to correctly close the streams application
		Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
	}

	private static JsonNode newBalanceb(JsonNode transactionr, JsonNode Report) {
		// create a new balance json object
		ObjectNode newBalanceb = JsonNodeFactory.instance.objectNode();
		newBalanceb.put("SenderAccountnumberb", transactionr.get("SenderAccountnumber").asInt());
		newBalanceb.put("ReceiverAccountnumber", transactionr.get("ReceiverAccountnumber").asInt());
		newBalanceb.put("Reportb", transactionr.get("Report"));
		newBalanceb.put("Amountbb", transactionr.get("Amountb").asInt());
		Instant now = Instant.now();
		newBalanceb.put("time", now.toString());
		return newBalanceb;
	}

}
