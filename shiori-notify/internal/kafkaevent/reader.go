package kafkaevent

import (
	"context"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	kafka "github.com/segmentio/kafka-go"
)

type kafkaGoReader struct {
	reader *kafka.Reader
}

func NewReaderFactory(cfg config.Config) ReaderFactory {
	return func() (Reader, error) {
		readerConfig := kafka.ReaderConfig{
			Brokers:     cfg.KafkaBrokers,
			GroupID:     cfg.KafkaGroupID,
			StartOffset: kafka.LastOffset,
			MinBytes:    1,
			MaxBytes:    10e6,
		}
		if len(cfg.KafkaTopics) == 1 {
			readerConfig.Topic = cfg.KafkaTopics[0]
		} else {
			readerConfig.GroupTopics = cfg.KafkaTopics
		}
		return &kafkaGoReader{
			reader: kafka.NewReader(readerConfig),
		}, nil
	}
}

func (r *kafkaGoReader) Read(ctx context.Context) ([]byte, error) {
	msg, err := r.reader.ReadMessage(ctx)
	if err != nil {
		return nil, err
	}
	return msg.Value, nil
}

func (r *kafkaGoReader) Close() error {
	return r.reader.Close()
}
