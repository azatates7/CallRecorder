using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using CallRecorderMobileApp.Api.Data;
using CallRecorderMobileApp.Api.Models;
using CallRecorderMobileApp.Api.Services;

namespace CallRecorderMobileApp.Api.Controllers;

[ApiController]
[Route("api/recordings")]
public class RecordingsController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly IRecordingStorageService _storage;
    private readonly IRedisCacheService _cache;
    private readonly ILogger<RecordingsController> _logger;

    public RecordingsController(
        AppDbContext db,
        IRecordingStorageService storage,
        IRedisCacheService cache,
        ILogger<RecordingsController> logger)
    {
        _db = db;
        _storage = storage;
        _cache = cache;
        _logger = logger;
    }

    // 1) Telefon uygulaması arama başladığında bu endpoint'i çağırır
    [HttpPost]
    public async Task<ActionResult<CreateRecordingResponse>> Create([FromBody] CreateRecordingRequest request)
    {
        var recording = new CallRecording
        {
            DeviceId = request.DeviceId,
            PhoneNumber = request.PhoneNumber,
            Direction = request.Direction,
            StartedAt = request.StartedAt,
            Status = "pending",
        };

        _db.CallRecordings.Add(recording);
        await _db.SaveChangesAsync();

        return Ok(new CreateRecordingResponse(recording.Id));
    }

    // 2) Arama bittiğinde ses dosyası buraya yüklenir
    [HttpPost("{id:guid}/upload")]
    [RequestSizeLimit(200_000_000)] // ~200MB üst sınır
    public async Task<IActionResult> Upload(Guid id, IFormFile file)
    {
        var recording = await _db.CallRecordings.FirstOrDefaultAsync(r => r.Id == id);
        if (recording == null) return NotFound();

        if (file == null || file.Length == 0)
            return BadRequest("Dosya bulunamadı");

        var storagePath = await _storage.SaveAsync(id, file);

        recording.StoragePath = storagePath;
        recording.Status = "uploaded";
        await _db.SaveChangesAsync();

        // Sık erişilen "son kayıtlar" listesini invalide et
        await _cache.RemoveAsync($"recordings:device:{recording.DeviceId}:page1");

        return Ok(new { success = true, storagePath });
    }

    // 3) Süre/boyut bilgisiyle kaydı sonlandırır
    [HttpPut("{id:guid}/finalize")]
    public async Task<IActionResult> Finalize(Guid id, [FromBody] FinalizeRecordingRequest request)
    {
        var recording = await _db.CallRecordings.FirstOrDefaultAsync(r => r.Id == id);
        if (recording == null) return NotFound();

        recording.DurationSeconds = request.DurationSeconds;
        recording.FileSizeBytes = request.FileSizeBytes;
        recording.Status = "finalized";
        await _db.SaveChangesAsync();

        return Ok(new { success = true });
    }

    // 4) Kayıtları listeler — Redis'te kısa süreli cache'lenir
    [HttpGet]
    public async Task<ActionResult<RecordingListResponse>> List(
        [FromQuery] int page = 1,
        [FromQuery] int pageSize = 20)
    {
        var cacheKey = $"recordings:list:page{page}:size{pageSize}";
        var cached = await _cache.GetAsync<RecordingListResponse>(cacheKey);
        if (cached != null) return Ok(cached);

        var query = _db.CallRecordings.OrderByDescending(r => r.StartedAt);

        var totalCount = await query.CountAsync();
        var items = await query
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .Select(r => new RecordingListItem(r.Id, r.PhoneNumber, r.Direction, r.StartedAt, r.DurationSeconds, r.Status))
            .ToListAsync();

        var response = new RecordingListResponse(items, page, pageSize, totalCount);
        await _cache.SetAsync(cacheKey, response, TimeSpan.FromSeconds(30));

        return Ok(response);
    }
}
