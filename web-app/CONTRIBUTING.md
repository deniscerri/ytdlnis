# Contributing to YTDLnis Web

Thank you for your interest in contributing! This guide will help you get started.

## Code of Conduct

- Be respectful and inclusive
- Help others learn and grow
- Focus on constructive feedback
- Report issues and bugs responsibly

## How Can I Contribute?

### Reporting Bugs

1. Check if the bug already exists in Issues
2. If not, create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Your OS and Node.js version
   - Screenshots/videos if applicable

### Suggesting Features

1. Check existing feature requests
2. Create a new issue with:
   - Clear use case
   - Why it's useful
   - How it might work
   - Mock-ups if applicable

### Code Contributions

#### Setup Development Environment

```bash
# Clone the repo
git clone https://github.com/ytdlnis/ytdlnis-web.git
cd ytdlnis-web

# Install dependencies
npm run install-all

# Start development servers
npm run dev
```

#### Making Changes

1. **Fork the repository**
2. **Create a branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes**
4. **Test thoroughly**
5. **Commit with clear messages:**
   ```bash
   git commit -m "Add feature: description"
   ```
6. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```
7. **Open a Pull Request**

## Development Guidelines

### Project Structure

```
ytdlnis-web/
├── server/           # Node.js backend
│   ├── index.js     # Main server
│   ├── database.js  # SQLite operations
│   └── ytdlp-manager.js
├── client/          # React frontend
│   └── src/
│       ├── pages/
│       ├── components/
│       └── context/
└── package.json
```

### Coding Standards

**JavaScript/Node.js:**
- Use ES6+ features
- Follow existing code style
- Add comments for complex logic
- Handle errors properly

**React:**
- Use functional components
- Use hooks (useState, useEffect, etc.)
- Follow Material-UI patterns
- Keep components small and focused

**General:**
- Write descriptive variable names
- Keep functions under 50 lines
- No console.logs in production
- Add JSDoc comments for functions

### Testing

Before submitting:

```bash
# Test the full app
npm start

# Test specific features:
# - Download audio
# - Download video
# - Cancel download
# - View history
# - Change settings
```

### Pull Request Guidelines

**Good PR:**
- Clear title and description
- Links to related issue
- Screenshots/videos of changes
- Tested on your machine
- No merge conflicts
- Small, focused changes

**PR Description Template:**
```markdown
## What does this PR do?
Brief description

## Related Issue
Fixes #123

## Changes Made
- Change 1
- Change 2

## Testing Done
- Tested on Windows 10
- Tested downloads
- Tested settings

## Screenshots
(if applicable)
```

## Areas Needing Help

### High Priority
- [ ] Format selection UI improvements
- [ ] Playlist download support
- [ ] Download scheduling
- [ ] Progress notifications
- [ ] Error recovery

### Medium Priority
- [ ] Cookie management UI
- [ ] Command templates
- [ ] Cut video feature
- [ ] Subtitle download
- [ ] Theme customization

### Low Priority
- [ ] Keyboard shortcuts
- [ ] Download statistics
- [ ] Export/import settings
- [ ] Multi-language support

## Community

- **GitHub Issues:** Bug reports and features
- **Discord:** Real-time chat (link TBD)
- **Discussions:** Questions and ideas

## Recognition

Contributors will be:
- Listed in README.md
- Mentioned in release notes
- Celebrated in the community

## Questions?

Feel free to:
- Open an issue for questions
- Ask in Discussions
- Reach out to maintainers

---

Thank you for contributing! Every contribution, no matter how small, helps make YTDLnis Web better.

