package com.example.redislab.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 경쟁 상황을 매번 똑같이 재현하려고 넣은 멈춤 지점.
 *
 * <p>실제 서비스라면 두 요청의 순서는 운에 달려 있어서, "가끔" 정합성이 깨집니다. 연습장에서는
 * 시나리오가 특정 스레드를 특정 지점에서 세워 두었다가 원하는 순서로 풀어 줘서, 그 "가끔"을 항상
 * 일어나게 만듭니다. 시나리오가 걸어 두지 않은 지점은 그냥 지나갑니다. 화면에서 직접 누를 때는
 * 아무 영향이 없습니다.
 */
@Component
public class Gate {

    private record Hold(CountDownLatch arrived, CountDownLatch release) {
    }

    private final Map<String, Hold> holds = new ConcurrentHashMap<>();
    private final Timeline timeline;

    public Gate(Timeline timeline) {
        this.timeline = timeline;
    }

    /** thread 라는 이름의 스레드가 point 에 오면 세워 두라고 걸어 둡니다. */
    public void arm(String point, String thread) {
        holds.put(key(point, thread), new Hold(new CountDownLatch(1), new CountDownLatch(1)));
    }

    /** 서비스 코드 안에서 부릅니다. 걸려 있지 않으면 바로 지나갑니다. */
    public void pass(String point) {
        Hold hold = holds.get(key(point, Thread.currentThread().getName()));
        if (hold == null) {
            return;
        }
        timeline.log("(멈춤) " + point);
        hold.arrived().countDown();
        await(hold.release());
        timeline.log("(다시 출발) " + point);
    }

    /** 그 스레드가 멈춤 지점에 도착할 때까지 기다립니다. */
    public void awaitArrival(String point, String thread) {
        await(holds.get(key(point, thread)).arrived());
    }

    public void release(String point, String thread) {
        holds.get(key(point, thread)).release().countDown();
    }

    public void clear() {
        holds.values().forEach(h -> h.release().countDown());
        holds.clear();
    }

    private static String key(String point, String thread) {
        return point + "@" + thread;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("멈춤 지점에서 10초 넘게 기다렸습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
