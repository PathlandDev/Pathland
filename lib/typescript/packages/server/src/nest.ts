import "reflect-metadata";
import {
  Injectable,
  SetMetadata,
  type CallHandler,
  type ExecutionContext,
  type NestInterceptor,
} from "@nestjs/common";
import type { Observable } from "rxjs";
import { of } from "rxjs";
import type { PathlandRegistry } from "./registry";

export const PATHLAND_VIEW = "pathland:view";

/** Mark a Nest controller/route as Pathland-rendered. */
export function PathlandView(): MethodDecorator & ClassDecorator {
  return (target: object, key?: string | symbol, descriptor?: PropertyDescriptor) => {
    SetMetadata(PATHLAND_VIEW, true)(target, key as string, descriptor as PropertyDescriptor);
  };
}

interface NestRequest {
  path: string;
  query?: Record<string, unknown>;
}

interface NestResponse {
  type(value: string): void;
}

/** Serve SSR HTML for routes marked with `@PathlandView()`. */
@Injectable()
export class PathlandInterceptor implements NestInterceptor {
  constructor(private readonly registry: PathlandRegistry) {}

  async intercept(context: ExecutionContext, next: CallHandler): Promise<Observable<unknown>> {
    if (context.getType() !== "http") return next.handle();
    const marked =
      Reflect.getMetadata(PATHLAND_VIEW, context.getHandler()) ||
      Reflect.getMetadata(PATHLAND_VIEW, context.getClass());
    if (!marked) return next.handle();

    const http = context.switchToHttp();
    const request = http.getRequest<NestRequest>();
    const response = http.getResponse<NestResponse>();
    const wid = typeof request.query?.wid === "string" ? request.query.wid : undefined;
    const html = await this.registry.renderHtml(request.path || "/", wid);
    response.type("html");
    return of(html);
  }
}
