package metrics

import (
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

const labelUnknown = "unknown"

var (
	registerOnce sync.Once

	wsConnections   prometheus.Gauge
	wsPushTotal     *prometheus.CounterVec
	mqConsumeTotal  *prometheus.CounterVec
	mqRouteDuration *prometheus.HistogramVec
)

func register() {
	registerOnce.Do(func() {
		wsConnections = prometheus.NewGauge(prometheus.GaugeOpts{
			Name: "shiori_notify_ws_connections",
			Help: "Current number of active websocket connections",
		})
		wsPushTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_ws_push_total",
			Help: "Total websocket push attempts grouped by result and event type",
		}, []string{"result", "event_type"})
		mqConsumeTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_mq_consume_total",
			Help: "Total MQ consumed events grouped by result and event type",
		}, []string{"result", "event_type"})
		mqRouteDuration = prometheus.NewHistogramVec(prometheus.HistogramOpts{
			Name:    "shiori_notify_mq_route_duration_seconds",
			Help:    "MQ event routing duration in seconds grouped by event type",
			Buckets: prometheus.DefBuckets,
		}, []string{"event_type"})

		prometheus.MustRegister(wsConnections, wsPushTotal, mqConsumeTotal, mqRouteDuration)
	})
}

func Handler() http.Handler {
	register()
	return promhttp.Handler()
}

func SetWSConnections(count int) {
	register()
	wsConnections.Set(float64(count))
}

func AddWSPush(result, eventType string, count int) {
	if count <= 0 {
		return
	}
	register()
	wsPushTotal.WithLabelValues(sanitizeLabel(result), sanitizeLabel(eventType)).Add(float64(count))
}

func IncMQConsume(result, eventType string) {
	register()
	mqConsumeTotal.WithLabelValues(sanitizeLabel(result), sanitizeLabel(eventType)).Inc()
}

func ObserveMQRouteDuration(eventType string, duration time.Duration) {
	register()
	mqRouteDuration.WithLabelValues(sanitizeLabel(eventType)).Observe(duration.Seconds())
}

func sanitizeLabel(value string) string {
	normalized := strings.TrimSpace(value)
	if normalized == "" {
		return labelUnknown
	}
	return normalized
}
