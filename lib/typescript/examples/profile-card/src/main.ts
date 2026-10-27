import { createProfileCardServer } from "./server";

const port = Number(process.env.PORT ?? 4317);
const { server } = await createProfileCardServer();
server.listen(port, "127.0.0.1", () => {
  console.log(`profile-card listening on http://127.0.0.1:${port}`);
});
