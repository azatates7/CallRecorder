using StackExchange.Redis;
using System.Text.Json;

namespace CallRecorderMobileApp.Api.Services;

public interface IRedisCacheService
{
    Task SetAsync<T>(string key, T value, TimeSpan? ttl = null);
    Task<T?> GetAsync<T>(string key);
    Task RemoveAsync(string key);
}

public class RedisCacheService : IRedisCacheService
{
    private readonly IConnectionMultiplexer _redis;

    public RedisCacheService(IConnectionMultiplexer redis)
    {
        _redis = redis;
    }

    private IDatabase Db => _redis.GetDatabase();

    public async Task SetAsync<T>(string key, T value, TimeSpan? ttl = null)
    {
        var json = JsonSerializer.Serialize(value);
        await Db.StringSetAsync(key, json, ttl ?? TimeSpan.FromMinutes(30));
    }

    public async Task<T?> GetAsync<T>(string key)
    {
        var json = await Db.StringGetAsync(key);
        if (json.IsNullOrEmpty) return default;
        return JsonSerializer.Deserialize<T>(json.ToString());
    }

    public async Task RemoveAsync(string key)
    {
        await Db.KeyDeleteAsync(key);
    }
}
