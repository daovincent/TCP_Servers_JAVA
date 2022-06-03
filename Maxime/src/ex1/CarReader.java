package ex1;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class CarReader implements Reader<String> {
    private final ShortReader shortReader = new ShortReader();
    private ByteBuffer internalBuffer = ByteBuffer.allocate(Short.MAX_VALUE); // write-mode
    private StringBuilder sb = new StringBuilder();
    private State state = State.WAITING_SHORT;
    private Short nbChar;
    private Short occurrence;
    private byte length;
    private int iter;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAITING_SHORT) {
            var status = shortReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                nbChar = shortReader.get();
                iter = 0;
                state = State.WAITING_OCC;
                shortReader.reset();
            } else {
                return status;
            }
        }

        if (state == State.WAITING_OCC) {
            var status = shortReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                occurrence = shortReader.get();
                state = State.WAITING_BYTE;
                shortReader.reset();
            } else {
                return status;
            }
        }

        if (state == State.WAITING_BYTE) {
            length = buffer.get();
            state = State.WAITING_CAR;
            internalBuffer = ByteBuffer.allocate(length);
        }

        if (state == State.WAITING_CAR) {
            iter++;
            buffer.flip();
            try {
                if (buffer.remaining() <= internalBuffer.remaining()) {
                    internalBuffer.put(buffer);
                } else {
                    var oldLimit = buffer.limit();
                    buffer.limit(internalBuffer.remaining());
                    internalBuffer.put(buffer);
                    buffer.limit(oldLimit);
                }
            } finally {
                buffer.compact();
            }

            if (internalBuffer.hasRemaining()) {
                return ProcessStatus.REFILL;
            }

            internalBuffer.flip();
            sb.append(StandardCharsets.UTF_8.decode(internalBuffer)).append(" : ").append(occurrence).append("\n");

            if (iter == nbChar) {
                state = State.DONE;
                return ProcessStatus.DONE;
            } else {
                state = State.WAITING_OCC;
                return ProcessStatus.REFILL;
            }
        }

        throw new AssertionError();
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return sb.toString();
    }

    @Override
    public void reset() {
        state = State.WAITING_SHORT;
        internalBuffer.clear();
        sb = new StringBuilder();
    }

    private enum State {
        DONE, WAITING_SHORT, WAITING_OCC, WAITING_BYTE, WAITING_CAR, ERROR
    }
}