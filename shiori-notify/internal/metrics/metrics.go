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

	wsConnections     prometheus.Gauge
	wsPushTotal       *prometheus.CounterVec
	mqConsumeTotal    *prometheus.CounterVec
	mqRouteDuration   *prometheus.HistogramVec
	replayQueryTotal  *prometheus.CounterVec
	replayEventsTotal *prometheus.CounterVec
	storeWriteTotal   *prometheus.CounterVec
	readOpTotal       *prometheus.CounterVec
	readMarkedTotal   *prometheus.CounterVec
	authFailureTotal  *prometheus.CounterVec
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
		replayQueryTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_replay_query_total",
			Help: "Total replay queries grouped by source and result",
		}, []string{"source", "result"})
		replayEventsTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_replay_events_total",
			Help: "Total replay events delivered grouped by source",
		}, []string{"source"})
		storeWriteTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_store_write_total",
			Help: "Total notification store writes grouped by driver and result",
		}, []string{"driver", "result"})
		readOpTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_read_op_total",
			Help: "Total read operations grouped by action and result",
		}, []string{"action", "result"})
		readMarkedTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_read_marked_total",
			Help: "Total marked-read events grouped by action",
		}, []string{"action"})
		authFailureTotal = prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "shiori_notify_auth_failure_total",
			Help: "Total auth failures grouped by source and reason",
		}, []string{"source", "reason"})

		prometheus.MustRegister(
			wsConnections,
			wsPushTotal,
			mqConsumeTotal,
			mqRouteDuration,
			replayQueryTotal,
			replayEventsTotal,
			storeWriteTotal,
			readOpTotal,
			readMarkedTotal,
			authFailureTotal,
		)
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

func IncReplayQuery(source, result string) {
	register()
	replayQueryTotal.WithLabelValues(sanitizeLabel(source), sanitizeLabel(result)).Inc()
}

func AddReplayEvents(source string, count int) {
	if count <= 0 {
		return
	}
	register()
	replayEventsTotal.WithLabelValues(sanitizeLabel(source)).Add(float64(count))
}

func IncStoreWrite(driver, result string) {
	register()
	storeWriteTotal.WithLabelValues(
		sanitizeLabel(driver),
		sanitizeLabel(result),
	).Inc()
}

func IncReadOp(action, result string) {
	register()
	readOpTotal.WithLabelValues(
		sanitizeLabel(action),
		sanitizeLabel(result),
	).Inc()
}

func AddReadMarked(action string, count int) {
	if count <= 0 {
		return
	}
	register()
	readMarkedTotal.WithLabelValues(sanitizeLabel(action)).Add(float64(count))
}

func IncAuthFailure(source, reason string) {
	register()
	authFailureTotal.WithLabelValues(
		sanitizeLabel(source),
		sanitizeLabel(reason),
	).Inc()
}

func sanitizeLabel(value string) string {
	normalized := strings.TrimSpace(value)
	if normalized == "" {
		return labelUnknown
	}
	return normalized
}
