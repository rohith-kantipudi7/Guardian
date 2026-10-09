from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from app.realtime import broadcaster

router = APIRouter(tags=["realtime"])


@router.websocket("/ws/emergency")
async def emergency_ws(ws: WebSocket) -> None:
    await broadcaster.connect(ws)
    try:
        await ws.send_json({"type": "CONNECTED"})
        while True:
            # Dashboards only listen; any inbound text is treated as a keep-alive ping.
            msg = await ws.receive_text()
            if msg == "ping":
                await ws.send_json({"type": "PONG"})
    except WebSocketDisconnect:
        pass
    finally:
        await broadcaster.disconnect(ws)
