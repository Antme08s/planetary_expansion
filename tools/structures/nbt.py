"""NBT mínimo (lectura/escritura, big-endian, gzip) para las estructuras."""
import gzip, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STRING, LIST, COMPOUND, IARR, LARR = range(13)


class Tag:
    __slots__ = ("type", "value")

    def __init__(self, t, v):
        self.type, self.value = t, v

    def __repr__(self):
        return f"Tag({self.type}, {self.value!r})"


def _read(t, b, i):
    if t == BYTE: return struct.unpack_from(">b", b, i)[0], i + 1
    if t == SHORT: return struct.unpack_from(">h", b, i)[0], i + 2
    if t == INT: return struct.unpack_from(">i", b, i)[0], i + 4
    if t == LONG: return struct.unpack_from(">q", b, i)[0], i + 8
    if t == FLOAT: return struct.unpack_from(">f", b, i)[0], i + 4
    if t == DOUBLE: return struct.unpack_from(">d", b, i)[0], i + 8
    if t == BARR:
        n = struct.unpack_from(">i", b, i)[0]; i += 4
        return bytes(b[i:i + n]), i + n
    if t == STRING:
        n = struct.unpack_from(">H", b, i)[0]; i += 2
        return b[i:i + n].decode("utf-8"), i + n
    if t == LIST:
        et = b[i]; n = struct.unpack_from(">i", b, i + 1)[0]; i += 5
        out = []
        for _ in range(n):
            v, i = _read(et, b, i); out.append(Tag(et, v))
        return (et, out), i
    if t == COMPOUND:
        out = {}
        while True:
            ct = b[i]; i += 1
            if ct == END: return out, i
            n = struct.unpack_from(">H", b, i)[0]; i += 2
            name = b[i:i + n].decode("utf-8"); i += n
            v, i = _read(ct, b, i); out[name] = Tag(ct, v)
    if t == IARR:
        n = struct.unpack_from(">i", b, i)[0]; i += 4
        return list(struct.unpack_from(f">{n}i", b, i)), i + 4 * n
    if t == LARR:
        n = struct.unpack_from(">i", b, i)[0]; i += 4
        return list(struct.unpack_from(f">{n}q", b, i)), i + 8 * n
    raise ValueError(t)


def _write(t, v, out):
    if t == BYTE: out += struct.pack(">b", v)
    elif t == SHORT: out += struct.pack(">h", v)
    elif t == INT: out += struct.pack(">i", v)
    elif t == LONG: out += struct.pack(">q", v)
    elif t == FLOAT: out += struct.pack(">f", v)
    elif t == DOUBLE: out += struct.pack(">d", v)
    elif t == BARR: out += struct.pack(">i", len(v)) + v
    elif t == STRING:
        e = v.encode("utf-8"); out += struct.pack(">H", len(e)) + e
    elif t == LIST:
        et, items = v
        if not items: et = END if et is None else et
        out += bytes([et]) + struct.pack(">i", len(items))
        for it in items: _write(et, it.value, out)
    elif t == COMPOUND:
        for name, tag in v.items():
            e = name.encode("utf-8")
            out += bytes([tag.type]) + struct.pack(">H", len(e)) + e
            _write(tag.type, tag.value, out)
        out += b"\x00"
    elif t == IARR: out += struct.pack(f">i{len(v)}i", len(v), *v)
    elif t == LARR: out += struct.pack(f">i{len(v)}q", len(v), *v)
    else: raise ValueError(t)


def load(path):
    b = gzip.decompress(open(path, "rb").read())
    assert b[0] == COMPOUND
    n = struct.unpack_from(">H", b, 1)[0]
    v, _ = _read(COMPOUND, b, 3 + n)
    return v


def save(path, root):
    out = bytearray(b"\x0a\x00\x00")
    _write(COMPOUND, root, out)
    open(path, "wb").write(gzip.compress(bytes(out), mtime=0))
