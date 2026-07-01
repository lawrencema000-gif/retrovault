-- Pin search_path and prevent the trigger helpers from being called as RPC endpoints.
alter function public.set_updated_at() set search_path = '';

revoke execute on function public.set_updated_at() from anon, authenticated, public;
revoke execute on function public.handle_new_user() from anon, authenticated, public;
