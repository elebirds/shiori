package mq

import (
	"fmt"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	amqp "github.com/rabbitmq/amqp091-go"
)

func DeclareTopology(ch *amqp.Channel, cfg config.Config) error {
	exchanges := cfg.RabbitMQExchanges
	if len(exchanges) == 0 {
		exchanges = []string{cfg.RabbitMQExchange}
	}

	routingKeys := cfg.RabbitMQRoutingKeys
	if len(routingKeys) == 0 {
		routingKeys = []string{cfg.RabbitMQRoutingKey}
	}

	for _, exchange := range exchanges {
		if err := ch.ExchangeDeclare(
			exchange,
			"topic",
			true,
			false,
			false,
			false,
			nil,
		); err != nil {
			return fmt.Errorf("declare exchange=%s failed: %w", exchange, err)
		}
	}

	if _, err := ch.QueueDeclare(
		cfg.RabbitMQQueue,
		true,
		false,
		false,
		false,
		nil,
	); err != nil {
		return fmt.Errorf("declare queue failed: %w", err)
	}

	for _, exchange := range exchanges {
		for _, routingKey := range routingKeys {
			if err := ch.QueueBind(
				cfg.RabbitMQQueue,
				routingKey,
				exchange,
				false,
				nil,
			); err != nil {
				return fmt.Errorf("bind queue failed exchange=%s key=%s: %w", exchange, routingKey, err)
			}
		}
	}

	return nil
}
