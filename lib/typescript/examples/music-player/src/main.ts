import { createMusicPlayerServer } from "./server";

const port = Number(process.env.PORT ?? 4318);
const { server } = await createMusicPlayerServer();
server.listen(port, "127.0.0.1", () => {
  console.log(`music-player listening on http://127.0.0.1:${port}`);
});
