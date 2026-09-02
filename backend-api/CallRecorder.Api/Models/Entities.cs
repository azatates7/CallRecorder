namespace CallRecorder.Api.Models;

public class Device
{
    public long Id { get; set; }
    public string DeviceId { get; set; } = default!;
    public string FcmToken { get; set; } = default!;
    public string Platform { get; set; } = "android";
    public DateTime RegisteredAt { get; set; } = DateTime.UtcNow;
    public DateTime LastSeenAt { get; set; } = DateTime.UtcNow;
}

public class CallRecording
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string DeviceId { get; set; } = default!;
    public string PhoneNumber { get; set; } = default!;
    public string Direction { get; set; } = "unknown"; // incoming / outgoing / unknown
    public DateTime StartedAt { get; set; }
    public int? DurationSeconds { get; set; }
    public long? FileSizeBytes { get; set; }
    public string? StoragePath { get; set; }
    public string Status { get; set; } = "pending"; // pending, uploaded, finalized, failed
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// DTO'lar
public record RegisterDeviceRequest(string DeviceId, string FcmToken, string Platform);
public record CreateRecordingRequest(string DeviceId, string PhoneNumber, DateTime StartedAt, string Direction);
public record CreateRecordingResponse(Guid RecordingId);
public record FinalizeRecordingRequest(int DurationSeconds, long FileSizeBytes);
public record RecordingListItem(Guid RecordingId, string PhoneNumber, string Direction, DateTime StartedAt, int? DurationSeconds, string Status);
public record RecordingListResponse(List<RecordingListItem> Items, int Page, int PageSize, int TotalCount);
