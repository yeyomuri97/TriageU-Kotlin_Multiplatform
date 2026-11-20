// Elimina cuenta y datos del usuario autenticado (verify_jwt activo).
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "method" }), { status: 405, headers: { ...CORS, "Content-Type": "application/json" } });
  }

  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: { ...CORS, "Content-Type": "application/json" } });
  }

  const url = Deno.env.get("SUPABASE_URL")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });

  const { data: userData, error: userErr } = await admin.auth.getUser(token);
  const userId = userData?.user?.id;
  if (userErr || !userId) {
    return new Response(JSON.stringify({ error: "unauthorized" }), { status: 401, headers: { ...CORS, "Content-Type": "application/json" } });
  }

  const { error: actErr } = await admin.from("activities").delete().eq("user_id", userId);
  if (actErr) {
    console.log(JSON.stringify({ event: "delete_activities_failed" }));
    return new Response(JSON.stringify({ error: "unavailable" }), { status: 503, headers: { ...CORS, "Content-Type": "application/json" } });
  }

  const { error: subErr } = await admin.from("subjects").delete().eq("user_id", userId);
  if (subErr) {
    console.log(JSON.stringify({ event: "delete_subjects_failed" }));
    return new Response(JSON.stringify({ error: "unavailable" }), { status: 503, headers: { ...CORS, "Content-Type": "application/json" } });
  }

  const { error: delErr } = await admin.auth.admin.deleteUser(userId);
  if (delErr) {
    console.log(JSON.stringify({ event: "delete_user_failed" }));
    return new Response(JSON.stringify({ error: "unavailable" }), { status: 503, headers: { ...CORS, "Content-Type": "application/json" } });
  }

  console.log(JSON.stringify({ event: "account_deleted" }));
  return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { ...CORS, "Content-Type": "application/json" } });
});