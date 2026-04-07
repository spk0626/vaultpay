package com.vaultpay.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration.
 *
 * RABBITMQ CONCEPTS (brief explainer for junior developers):
 *
 *   Exchange  → receives messages from publishers. Routes them to queues based on rules.
 *   Queue     → buffers messages until consumers process them.
 *   Binding   → the routing rule that connects an exchange to a queue.
 *   Routing Key → the label a publisher attaches to a message; the exchange uses it
 *                 to decide which queue(s) receive the message.
 *
 * OUR TOPOLOGY:
 *
 *   TopicExchange "vaultpay.events"
 *     │
 *     ├─ binding: "transaction.#"  ──► queue "vaultpay.notifications"
 *
 *   The "#" wildcard matches zero or more words. So routing keys like
 *   "transaction.transfer", "transaction.deposit" all route to our queue.
 *
 * WHY TOPIC EXCHANGE?
 *   DirectExchange routes on exact key match. TopicExchange uses patterns.
 *   If we add an "account.verified" event type later, it won't accidentally
 *   land in the transaction notification queue. Clean separation from day one.
 *
 * DEAD LETTER QUEUE (DLQ):
 *   If a message fails processing 3 times, RabbitMQ moves it to the DLQ
 *   instead of dropping it silently. We can inspect failed messages later.
 *   This is production-grade fault tolerance.
 */
@Configuration
public class RabbitMQConfig {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final String EXCHANGE_NAME      = "vaultpay.events";
    public static final String QUEUE_NAME         = "vaultpay.notifications";
    public static final String DLQ_NAME           = "vaultpay.notifications.dlq";
    public static final String DLX_NAME           = "vaultpay.events.dlx";  // Dead letter exchange
    public static final String ROUTING_KEY_PATTERN = "transaction.#";

    // ── Exchange declarations ─────────────────────────────────────────────────

    /** Main topic exchange — all VaultPay domain events are published here */
    @Bean                                                                         // @Bean tells Spring to manage this method's return value as a bean in the application context. Whenever another part of the app needs a TopicExchange, Spring will call this method to provide it.
    public TopicExchange vaultPayExchange() {
        return ExchangeBuilder
                .topicExchange(EXCHANGE_NAME)
                .durable(true)    // survives RabbitMQ restarts
                .build();
    }

    /** Dead letter exchange — receives messages that couldn't be processed */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(DLX_NAME)
                .durable(true)
                .build();
    }                                 // DirectExchange is sufficient for the DLX since we only have one DLQ and a fixed routing key.

    // ── Queue declarations ────────────────────────────────────────────────────

    /** Main notification queue with DLQ configuration */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder
                .durable(QUEUE_NAME)
                // After 3 failed delivery attempts → route to DLX
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    /** Dead letter queue — stores messages that failed after max retries */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    /** Route all "transaction.*" events from the main exchange to the notification queue */
    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange vaultPayExchange) {
        return BindingBuilder
                .bind(notificationQueue)
                .to(vaultPayExchange)
                .with(ROUTING_KEY_PATTERN);
    }

    /** Bind the DLQ to the dead letter exchange */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_NAME);
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    /**
     * Use JSON serialization for all messages instead of Java's built-in binary
     * serialization. JSON messages are human-readable in the RabbitMQ management UI
     * and interoperable with non-Java consumers.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {   // This bean is used by both RabbitTemplate (for sending) and the listener container factory (for receiving) to ensure consistent JSON serialization/deserialization across the app.
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configure RabbitTemplate to use JSON serialization.
     * This is used by DomainEventPublisher to send messages.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);  // ConnectionFactory is auto-configured by Spring Boot based on application.properties
        template.setMessageConverter(jsonMessageConverter());   // Use our JSON converter for outgoing messages
        return template;
    }

    /**
     * Configure the listener container factory to use JSON deserialization.
     * This is used by @RabbitListener in NotificationListener.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();  // This factory creates the listener container that manages @RabbitListener methods. We need to set the message converter here to ensure incoming messages are deserialized from JSON.
        factory.setConnectionFactory(connectionFactory);  // Set the connection factory for RabbitMQ connections
        factory.setMessageConverter(jsonMessageConverter());   // Use our JSON converter for incoming messages
        // Acknowledge manually so we only ACK after successful processing
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);  // AUTO means the container will automatically ACK messages if the listener method returns successfully, and will reject (NACK) if an exception is thrown. This works well with our DLQ setup.
        factory.setDefaultRequeueRejected(false); // failed messages → DLQ, not re-queued forever
        return factory;
    }


    // Explanation of ACKNOWLEDGE MODE:
    // - AUTO: the container automatically ACKs if the listener method returns without throwing an exception. If an exception is thrown, the message is rejected (NACKed) and, with our configuration, sent to the DLQ after 3 attempts.
    // - MANUAL: the listener method must call channel.basicAck() or channel.basicNack() explicitly. This gives more control but requires more boilerplate. AUTO is sufficient for our needs and works well with the DLQ setup.

    // Explanation of this class and its methods to a non-java developer using real life analogies:
    // - RabbitMQ is like a post office. Publishers drop off letters (messages) at the post office (exchange). The post office sorts the letters based on the address (routing key) and delivers them to the correct mailbox (queue). Consumers check their mailbox and read the letters.
    // - This configuration class sets up the post office's sorting rules and mailboxes. We have one main mailbox for notifications and a backup mailbox (DLQ) for letters that couldn't be delivered after multiple attempts.
    // - The jsonMessageConverter is like a translator that converts our Java objects into a format (JSON) that can be easily read and understood by other systems, and vice versa when receiving messages.
    // - The rabbitTemplate is like a mail carrier that we use to send letters (messages) to the post office (exchange). We configure it to use our translator (JSON converter) so that all outgoing messages are in the correct format.
    // - The rabbitListenerContainerFactory is like the setup for our mailbox. It tells the mailbox how to handle incoming letters (messages), including how to translate them from JSON back into Java objects and how to acknowledge receipt of the letters.
    // - The DLQ setup ensures that if a letter can't be delivered after multiple attempts (e.g., the recipient is unavailable), it gets sent to a special mailbox (DLQ) instead of being lost. This allows us to review and handle failed messages later, which is crucial for reliability in a production system.

    // - RabbitMQ -> Post Office
    // - Exchange -> Sorting Center
    // - Queue -> Mailbox
    // - Routing Key -> Address on the letter
    // - Message Converter -> Translator
    // - RabbitTemplate -> Mail Carrier (the person or vehicle that delivers mail). In our case, it's the component that sends messages to RabbitMQ.
    // - Listener Container Factory -> Mailbox Setup
    // - DLQ -> Backup Mailbox for undelivered letters


    // Flow of a message through the system:
    // 1. A transaction event occurs in the TransactionService (e.g., a transfer is completed).
    // 2. TransactionService calls publishEvent(), which uses RabbitTemplate to send a message to the "vaultpay.events" exchange with a routing key like "transaction.transfer".
    // 3. The exchange receives the message and routes it to the "vaultpay.notifications" queue based on the routing key pattern "transaction.#".
    // 4. The NotificationListener, which is listening to the "vaultpay.notifications" queue, receives the message. The listener container uses the jsonMessageConverter to deserialize the JSON message back into a Java object.
    // 5. The listener processes the message (e.g., sends an email notification). If processing succeeds, the message is ACKed and removed from the queue. If processing fails (e.g., an exception is thrown), the message is NACKed and, after 3 failed attempts, moved to the "vaultpay.notifications.dlq" for later inspection.

    // Is this observer pattern or mediator pattern or something else?
    // - This is primarily the Outbox Pattern, which is a way to reliably publish events in a distributed system. The RabbitMQ setup itself is not strictly an Observer or Mediator pattern, but it facilitates decoupled communication between components. The NotificationListener acts as an Observer of the events published to RabbitMQ, while the TransactionService is the Subject that generates those events. However, the overall architecture is more about reliable event-driven communication than fitting into a specific design pattern like Observer or Mediator.

}