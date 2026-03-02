package mq

import (
	"fmt"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	amqp "github.com/rabbitmq/amqp091-go"
)

func DeclareTopology(ch *amqp.Channel, cfg config.Config) error {
	if err := ch.ExchangeDeclare(
		cfg.RabbitMQExchange,
		"topic",
		true,
		false,
		false,
		false,
		nil,
	); err != nil {
		return fmt.Errorf("declare exchange: %w", err)
	}

	_, err := ch.QueueDeclare(
		cfg.RabbitMQQueue,
		true,
		false,
		false,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("declare queue: %w", err)
	}

	if err := ch.QueueBind(
		cfg.RabbitMQQueue,
		cfg.RabbitMQRoutingKey,
		cfg.RabbitMQExchange,
		false,
		nil,
	); err != nil {
		return fmt.Errorf("bind queue: %w", err)
	}

	return nil
}
