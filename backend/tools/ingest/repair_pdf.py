"""Some downloaded gazette PDFs have an HTML page appended after %%EOF. Usage: python3 repair_pdf.py in.pdf [out.pdf]"""
import subprocess, sys
src = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else src[:-4] + '.fixed.pdf'
data = open(src, 'rb').read()
i = data.rfind(b'%%EOF')
open(out + '.raw', 'wb').write(data[:i + 5] + b'\n')
subprocess.run(['qpdf', out + '.raw', out], check=False)
print('wrote', out, 'stripped', len(data) - i - 5, 'bytes')
