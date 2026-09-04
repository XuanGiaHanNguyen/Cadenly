from fastapi import FastAPI

from app.api.routes import router

app = FastAPI(title="Service B: Recording & AI Pipeline")
app.include_router(router)
